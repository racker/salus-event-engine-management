/*
 * Copyright 2019 Rackspace US, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rackspace.salus.event.manage.services;

import com.rackspace.salus.event.common.InfluxScope;
import com.rackspace.salus.event.discovery.EngineInstance;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.entities.EventEngineTask;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.model.kapacitor.DbRp;
import com.rackspace.salus.event.manage.model.kapacitor.Task;
import com.rackspace.salus.event.manage.model.kapacitor.Task.Status;
import com.rackspace.salus.event.manage.repositories.EventEngineTaskRepository;
import com.rackspace.salus.event.manage.services.TaskIdGenerator.TaskId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.rackspace.salus.event.manage.types.Comparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class TasksService {

  private final EventEnginePicker eventEnginePicker;
  private final RestTemplate restTemplate;
  private final EventEngineTaskRepository eventEngineTaskRepository;
  private final TaskIdGenerator taskIdGenerator;
  private final TickScriptBuilder tickScriptBuilder;
  private final AccountQualifierService accountQualifierService;

  @Autowired
  public TasksService(EventEnginePicker eventEnginePicker, RestTemplateBuilder restTemplateBuilder,
                      EventEngineTaskRepository eventEngineTaskRepository,
                      TaskIdGenerator taskIdGenerator, TickScriptBuilder tickScriptBuilder,
                      AccountQualifierService accountQualifierService) {
    this.eventEnginePicker = eventEnginePicker;
    this.restTemplate = restTemplateBuilder.build();
    this.eventEngineTaskRepository = eventEngineTaskRepository;
    this.taskIdGenerator = taskIdGenerator;
    this.tickScriptBuilder = tickScriptBuilder;
    this.accountQualifierService = accountQualifierService;
  }

  public EventEngineTask createTask(String tenantId, CreateTask in) {

    if (Comparator.convertString.get(in.getTaskParameters().getComparator()) == null) {
        throw new BackendException(new ResponseEntity<>(HttpStatus.BAD_REQUEST),
                "Invalid comparator " + in.getTaskParameters().getComparator());
    }

    final TaskId taskId = taskIdGenerator.generateTaskId(tenantId, in.getMeasurement());
    final Task task = Task.builder()
        .id(taskId.getKapacitorTaskId())
        .dbrps(Collections.singletonList(DbRp.builder()
            .db(accountQualifierService.convertFromTenant(tenantId))
            .rp(InfluxScope.INGEST_RETENTION_POLICY)
            .build()))
        .script(tickScriptBuilder.build(tenantId, in.getMeasurement(), in.getTaskParameters()))
        .status(Status.enabled)
        .build();

    for (EngineInstance engineInstance : eventEnginePicker.pickAll()) {
      log.debug("Sending task={} to kapacitor={}", taskId, engineInstance);

      final ResponseEntity<Task> response = restTemplate.postForEntity("http://{host}:{port}/kapacitor/v1/tasks",
          task,
          Task.class,
          engineInstance.getHost(), engineInstance.getPort()
      );

      if (response.getStatusCode().isError()) {
        String details = response.getBody() != null ? response.getBody().getError() : "";
        throw new BackendException(response,
            String.format("HTTP error while creating task=%s on instance=%s: %s", task, engineInstance, details)
        );
      }

      final Task respTask = response.getBody();
      if (respTask == null) {
        throw new BackendException(null,
            String.format("Empty engine response while creating task=%s on instance=%s", task, engineInstance)
        );
      }

      if (StringUtils.hasText(respTask.getError())) {
        throw new BackendException(response,
            String.format("Engine error while creating task=%s on instance=%s: %s", task, engineInstance, respTask.getError())
        );
      }
    }

    final EventEngineTask eventEngineTask = new EventEngineTask()
        .setId(taskId.getBaseId())
        .setTenantId(tenantId)
        .setMeasurement(in.getMeasurement())
        .setTaskId(taskId.getKapacitorTaskId())
        .setTaskParameters(in.getTaskParameters());

    return eventEngineTaskRepository.save(eventEngineTask);
  }

  public List<EventEngineTask> getTasks(String tenantId) {
    return eventEngineTaskRepository.findByTenantId(tenantId);
  }

  public List<EventEngineTask> getTasks(String tenantId, String measurement) {
    return eventEngineTaskRepository.findByTenantIdAndMeasurement(tenantId, measurement);
  }

  public void deleteTask(String tenantId, UUID taskDbId) {

    final EventEngineTask eventEngineTask = eventEngineTaskRepository.findById(taskDbId)
        .orElseThrow(() -> new NotFoundException("Unable to find the requested event engine task"));

    if (!eventEngineTask.getTenantId().equals(tenantId)) {
      log.info("Task={} was requested for deletion with incorrect tenant={}", taskDbId, tenantId);
      // but keep the exception vague to avoid leaking exploitable info
      throw new NotFoundException("Unable to find the requested event engine task");
    }

    eventEngineTaskRepository.delete(eventEngineTask);

    for (EngineInstance engineInstance : eventEnginePicker.pickAll()) {
      log.debug("Deleting kapacitorTask={} from instance={}", eventEngineTask.getTaskId(), engineInstance);
      restTemplate.delete("http://{host}:{port}/kapacitor/v1/tasks/{taskId}",
          engineInstance.getHost(),
          engineInstance.getPort(),
          eventEngineTask.getTaskId()
      );
    }
  }
}
