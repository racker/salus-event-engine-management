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
import com.rackspace.salus.event.manage.model.kapacitor.Var;
import com.rackspace.salus.event.manage.repositories.EventEngineTaskRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class TasksService {

  private final EventEnginePicker eventEnginePicker;
  private final RestTemplate restTemplate;
  private final EventEngineTaskRepository eventEngineTaskRepository;
  private final TaskIdGenerator taskIdGenerator;
  private final TickScriptBuilder tickScriptBuilder;

  @Autowired
  public TasksService(EventEnginePicker eventEnginePicker, RestTemplateBuilder restTemplateBuilder,
                      EventEngineTaskRepository eventEngineTaskRepository,
                      TaskIdGenerator taskIdGenerator, TickScriptBuilder tickScriptBuilder) {
    this.eventEnginePicker = eventEnginePicker;
    this.restTemplate = restTemplateBuilder.build();
    this.eventEngineTaskRepository = eventEngineTaskRepository;
    this.taskIdGenerator = taskIdGenerator;
    this.tickScriptBuilder = tickScriptBuilder;

  }

  public EventEngineTask createTask(String tenantId, CreateTask in) {

    final String taskId = taskIdGenerator.generateTaskId(tenantId, in.getMeasurement());
    final Task task = Task.builder()
        .id(taskId)
        .dbrps(Collections.singletonList(DbRp.builder()
            .db(tenantId)
            .rp(InfluxScope.INGEST_RETENTION_POLICY)
            .build()))
        .script(tickScriptBuilder.build(tenantId, in.getMeasurement(), in.getScenario()))
        .status(Status.enabled)
        .build();

    final EngineInstance engineInstance = eventEnginePicker
        .pickRecipient(tenantId, in.getMeasurement());

    log.debug("Sending task={} to kapacitor={}", taskId, engineInstance);

    final ResponseEntity<Task> response = restTemplate.postForEntity("http://{host}:{port}/kapacitor/v1/tasks",
        task,
        Task.class,
        engineInstance.getHost(), engineInstance.getPort()
    );

    if (response.getStatusCode().isError()) {
      String details = response.getBody() != null ? response.getBody().getError() : "";
      throw new BackendException(response, "Failed to create task: "+details);
    }

    final Task respTask = response.getBody();

    final EventEngineTask eventEngineTask = new EventEngineTask()
        .setTenantId(tenantId)
        .setMeasurement(in.getMeasurement())
        .setTaskId(taskId)
        .setAssignedPartition(engineInstance.getPartition())
        .setComputedTickScript(respTask.getScript())
        .setScenario(in.getScenario());

    return eventEngineTaskRepository.save(eventEngineTask);
  }

  private Map<String, Var> buildVars(Map<String, Object> vars) {
    return vars.entrySet().stream()
        .collect(Collectors.toMap(
            Entry::getKey,
            entry -> Var.from(entry.getValue())
        ));
  }

  public List<EventEngineTask> getTasks(String tenantId) {

    //TODO query the assigned kapacitor for state and stats details
    return eventEngineTaskRepository.findByTenantId(tenantId);
  }

  public void deleteTask(String tenantId, long taskDbId) {

    final EventEngineTask eventEngineTask = eventEngineTaskRepository.findById(taskDbId)
        .orElseThrow(() -> new NotFoundException("Unable to find the requested event engine task"));

    if (!eventEngineTask.getTenantId().equals(tenantId)) {
      log.info("Task={} was requested for deletion with incorrect tenant={}", taskDbId, tenantId);
      // but keep the exception vague to avoid leaking exploitable info
      throw new NotFoundException("Unable to find the requested event engine task");
    }

    eventEngineTaskRepository.delete(eventEngineTask);

    final EngineInstance engineInstance = eventEnginePicker
        .pickUsingPartition(eventEngineTask.getAssignedPartition());

    log.debug("Deleting kapacitorTask={} from instance={}", eventEngineTask.getTaskId(), engineInstance);
    restTemplate.delete("http://{host}:{port}/kapacitor/v1/tasks/{taskId}",
        engineInstance.getHost(),
        engineInstance.getPort(),
        eventEngineTask.getTaskId()
    );
  }

}
