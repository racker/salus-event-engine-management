/*
 * Copyright 2020 Rackspace US, Inc.
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
 *
 */

package com.rackspace.salus.event.manage.services;

import com.rackspace.salus.event.common.InfluxScope;
import com.rackspace.salus.event.discovery.EngineInstance;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.errors.BackendException;
import com.rackspace.salus.event.manage.errors.NotFoundException;
import com.rackspace.salus.event.manage.model.TaskCU;
import com.rackspace.salus.event.model.kapacitor.DbRp;
import com.rackspace.salus.event.model.kapacitor.Task;
import com.rackspace.salus.event.model.kapacitor.Task.Status;
import com.rackspace.salus.event.model.kapacitor.Task.Type;
import com.rackspace.salus.event.manage.services.KapacitorTaskIdGenerator.KapacitorTaskId;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.repositories.EventEngineTaskRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class TasksService {

  private final EventEnginePicker eventEnginePicker;
  private final RestTemplate restTemplate;
  private final EventEngineTaskRepository eventEngineTaskRepository;
  private final KapacitorTaskIdGenerator kapacitorTaskIdGenerator;
  private final TickScriptBuilder tickScriptBuilder;

  @Autowired
  public TasksService(EventEnginePicker eventEnginePicker, RestTemplateBuilder restTemplateBuilder,
                      EventEngineTaskRepository eventEngineTaskRepository,
                      KapacitorTaskIdGenerator kapacitorTaskIdGenerator,
                      TickScriptBuilder tickScriptBuilder) {
    this.eventEnginePicker = eventEnginePicker;
    this.restTemplate = restTemplateBuilder.build();
    this.eventEngineTaskRepository = eventEngineTaskRepository;
    this.kapacitorTaskIdGenerator = kapacitorTaskIdGenerator;
    this.tickScriptBuilder = tickScriptBuilder;
  }

  @Transactional
  public EventEngineTask createTask(String tenantId, TaskCU in) {

    final KapacitorTaskId taskId = kapacitorTaskIdGenerator.generateTaskId(tenantId, in.getMeasurement());
    final Task task = new Task()
        .setId(taskId.getKapacitorTaskId())
        .setType(Type.stream)
        .setDbrps(Collections.singletonList(new DbRp()
            .setDb(tenantId)
            .setRp(InfluxScope.INGEST_RETENTION_POLICY)
            ))
        .setScript(tickScriptBuilder.build(in.getMeasurement(), in.getTaskParameters()))
        .setStatus(Status.enabled);

    sendTaskToKapacitor(task, taskId);

    final EventEngineTask eventEngineTask = new EventEngineTask()
        .setId(taskId.getBaseId())
        .setTenantId(tenantId)
        .setMeasurement(in.getMeasurement())
        .setName(in.getName())
        .setKapacitorTaskId(taskId.getKapacitorTaskId())
        .setTaskParameters(in.getTaskParameters());

    return eventEngineTaskRepository.save(eventEngineTask);
  }

  public Optional<EventEngineTask> getTask(String tenantId, UUID id) {
    return eventEngineTaskRepository.findByTenantIdAndId(tenantId, id);
  }

  public Page<EventEngineTask> getTasks(String tenantId, Pageable page) {
    return eventEngineTaskRepository.findByTenantId(tenantId, page);
  }

  @Transactional
  public void deleteTask(String tenantId, UUID taskDbId) {

    final EventEngineTask eventEngineTask = eventEngineTaskRepository.findById(taskDbId)
        .orElseThrow(() -> new NotFoundException("Unable to find the requested event engine task"));

    if (!eventEngineTask.getTenantId().equals(tenantId)) {
      log.info("Task={} was requested for deletion with incorrect tenant={}", taskDbId, tenantId);
      // but keep the exception vague to avoid leaking exploitable info
      throw new NotFoundException("Unable to find the requested event engine task");
    }

    deleteTaskFromKapacitors(eventEngineTask.getKapacitorTaskId(), eventEnginePicker.pickAll(),
        false);

    eventEngineTaskRepository.delete(eventEngineTask);
  }

  private void deleteTaskFromKapacitors(String kapacitorTaskId,
                                        Collection<EngineInstance> engineInstances,
                                        boolean rollback) {
    if (!rollback && engineInstances.isEmpty()) {
      throw new IllegalStateException("No event engine instances are available");
    }

    for (EngineInstance engineInstance : engineInstances) {
      log.debug("Deleting kapacitorTask={} from instance={}", kapacitorTaskId, engineInstance);
      try {
        restTemplate.delete("http://{host}:{port}/kapacitor/v1/tasks/{kapacitorTaskId}",
            engineInstance.getHost(),
            engineInstance.getPort(),
            kapacitorTaskId
        );
      } catch (RestClientException e) {
        log.warn("Failed to delete kapacitorTask={} from engineInstance={}",
            kapacitorTaskId, engineInstance, e);
      }
    }
  }

  public void deleteAllTasksForTenant(String tenant) {
    eventEngineTaskRepository.findByTenantId(tenant, Pageable.unpaged())
        .forEach(task -> deleteTask(tenant, task.getId()));
  }

  @Transactional
  public EventEngineTask updateTask(String tenantId, UUID uuid, TaskCU taskCU) {
    EventEngineTask eventEngineTask = eventEngineTaskRepository.findByTenantIdAndId(tenantId, uuid).orElseThrow(() ->
        new NotFoundException(String.format("No Event found for %s on tenant %s",
            uuid, tenantId)));
    log.info("Updating event engine task={} with new values={}", uuid, taskCU);
    boolean needsUpdate = false;
    if(!StringUtils.isEmpty(taskCU.getName()) && !eventEngineTask.getName().equals(taskCU.getName()))  {
      log.info("changing name={} to updatedName={} ",eventEngineTask.getName(), taskCU.getName());
      eventEngineTask.setName(taskCU.getName());
    }
    if(!StringUtils.isEmpty(taskCU.getMeasurement()) && !taskCU.getMeasurement().equals(eventEngineTask.getMeasurement())){
      log.info("changing measurement={} to {} ",eventEngineTask.getMeasurement(), taskCU.getMeasurement());
      eventEngineTask.setMeasurement(taskCU.getMeasurement());
      needsUpdate = true;
    }
    if(taskCU.getTaskParameters() != null && !taskCU.getTaskParameters().equals(eventEngineTask.getTaskParameters())){
      log.info("changing task parameters={} to {} ",eventEngineTask.getTaskParameters(), taskCU.getTaskParameters());
      eventEngineTask.setTaskParameters(taskCU.getTaskParameters());
      needsUpdate = true;
    }

    if(needsUpdate) {
      eventEngineTask = changeMeasurementAndTaskParameters(eventEngineTask);
    }
    return eventEngineTaskRepository.save(eventEngineTask);
  }

  private EventEngineTask changeMeasurementAndTaskParameters(EventEngineTask eventEngineTask) {
    log.info("deleting existing kapacitors event and creating new events");
    // Remove all kapacitor tasks and its associated ids
    deleteTaskFromKapacitors(eventEngineTask.getKapacitorTaskId(), eventEnginePicker.pickAll(),
        false);

    //update existing KapacitorTaskId with tenant and measurement data
    final KapacitorTaskId taskId = kapacitorTaskIdGenerator
        .updateTaskId(eventEngineTask.getTenantId(), eventEngineTask.getMeasurement(), eventEngineTask.getId());
    eventEngineTask.setKapacitorTaskId(taskId.getKapacitorTaskId());

    final Task task = new Task()
        .setId(eventEngineTask.getKapacitorTaskId())
        .setType(Type.stream)
        .setDbrps(Collections.singletonList(new DbRp()
            .setDb(eventEngineTask.getTenantId())
            .setRp(InfluxScope.INGEST_RETENTION_POLICY)
        ))
        .setScript(tickScriptBuilder.build(eventEngineTask.getMeasurement(), eventEngineTask.getTaskParameters()))
        .setStatus(Status.enabled);

    sendTaskToKapacitor(task, taskId);
    return eventEngineTask;
  }

  private void sendTaskToKapacitor(Task task, KapacitorTaskId taskId) {
    final List<EngineInstance> applied = new ArrayList<>();

    final Collection<EngineInstance> engineInstances = eventEnginePicker.pickAll();
    if (engineInstances.isEmpty()) {
      throw new IllegalStateException("No event engine instances are available");
    }

    for (EngineInstance engineInstance : engineInstances) {
      log.debug("Sending task={} to kapacitor={}", taskId, engineInstance);

      final ResponseEntity<Task> response;
      try {
        response = restTemplate.postForEntity("http://{host}:{port}/kapacitor/v1/tasks",
            task,
            Task.class,
            engineInstance.getHost(), engineInstance.getPort()
        );
      } catch (RestClientException e) {

        // roll-back the submitted tasks
        deleteTaskFromKapacitors(taskId.getKapacitorTaskId(), applied, true);
        throw new BackendException(null,
            String.format("HTTP error while creating task=%s on instance=%s: %s", task, engineInstance, e.getMessage())
        );
      }

      if (response.getStatusCode().isError()) {
        String details = response.getBody() != null ? response.getBody().getError() : "";
        // roll-back the submitted tasks
        deleteTaskFromKapacitors(taskId.getKapacitorTaskId(), applied, false);
        throw new BackendException(response,
            String.format("HTTP error while creating task=%s on instance=%s: %s", task, engineInstance, details)
        );
      }

      final Task respTask = response.getBody();
      if (respTask == null) {
        // roll-back the submitted tasks
        deleteTaskFromKapacitors(taskId.getKapacitorTaskId(), applied, false);
        throw new BackendException(null,
            String.format("Empty engine response while creating task=%s on instance=%s", task, engineInstance)
        );
      }

      if (StringUtils.hasText(respTask.getError())) {
        // roll-back the submitted tasks
        deleteTaskFromKapacitors(taskId.getKapacitorTaskId(), applied, false);
        throw new BackendException(response,
            String.format("Engine error while creating task=%s on instance=%s: %s", task, engineInstance, respTask.getError())
        );
      }

      applied.add(engineInstance);
    }
  }
}
