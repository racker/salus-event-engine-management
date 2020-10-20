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

import com.rackspace.salus.common.config.MetricNames;
import com.rackspace.salus.common.config.MetricTagValues;
import com.rackspace.salus.common.config.MetricTags;
import com.rackspace.salus.event.discovery.EngineInstance;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.errors.BackendException;
import com.rackspace.salus.event.manage.errors.NotFoundException;
import com.rackspace.salus.event.manage.model.GenericTaskCU;
import com.rackspace.salus.event.manage.model.SalusTaskCU;
import com.rackspace.salus.event.manage.model.TaskCU;
import com.rackspace.salus.event.manage.services.KapacitorTaskIdGenerator.KapacitorTaskId;
import com.rackspace.salus.event.model.kapacitor.Task;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.subclass.GenericEventEngineTask;
import com.rackspace.salus.telemetry.entities.subclass.SalusEventEngineTask;
import com.rackspace.salus.telemetry.repositories.EventEngineTaskRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
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
  private final TaskGenerator taskGenerator;
  private final TickScriptBuilder tickScriptBuilder;


  MeterRegistry meterRegistry;

  // metrics counters
  private final Counter.Builder taskSuccess;

  @Autowired
  public TasksService(EventEnginePicker eventEnginePicker, RestTemplateBuilder restTemplateBuilder,
      EventEngineTaskRepository eventEngineTaskRepository,
      KapacitorTaskIdGenerator kapacitorTaskIdGenerator,
      TaskGenerator taskGenerator,
      TickScriptBuilder tickScriptBuilder, MeterRegistry meterRegistry) {
    this.eventEnginePicker = eventEnginePicker;
    this.restTemplate = restTemplateBuilder.build();
    this.eventEngineTaskRepository = eventEngineTaskRepository;
    this.kapacitorTaskIdGenerator = kapacitorTaskIdGenerator;
    this.taskGenerator = taskGenerator;
    this.tickScriptBuilder = tickScriptBuilder;

    this.meterRegistry = meterRegistry;
    taskSuccess = Counter.builder(MetricNames.SERVICE_OPERATION_SUCCEEDED).tag(MetricTags.SERVICE_METRIC_TAG,"Tasks");
  }

  @Transactional
  public EventEngineTask createTask(String tenantId, TaskCU in) {
    final EventEngineTask eventEngineTask = taskGenerator.createTask(tenantId, in);

    EventEngineTask eventEngineTaskSaved = eventEngineTaskRepository.save(eventEngineTask);
    taskSuccess.tags(
        MetricTags.OPERATION_METRIC_TAG, MetricTagValues.CREATE_OPERATION,
        MetricTags.OBJECT_TYPE_METRIC_TAG, "task")
        .register(meterRegistry).increment();

    return eventEngineTaskSaved;
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
    taskSuccess.tags(
        MetricTags.OPERATION_METRIC_TAG, MetricTagValues.REMOVE_OPERATION,
        MetricTags.OBJECT_TYPE_METRIC_TAG, "task")
        .register(meterRegistry).increment();
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
    boolean redeployTask = false;
    if (!StringUtils.isEmpty(taskCU.getName()) && !eventEngineTask.getName().equals(taskCU.getName()))  {
      log.info("changing name={} to updatedName={} ",eventEngineTask.getName(), taskCU.getName());
      eventEngineTask.setName(taskCU.getName());
    }

    if (taskCU.getTaskParameters() != null && !taskCU.getTaskParameters().equals(eventEngineTask.getTaskParameters())){
      log.info("changing task parameters={} to updated taskParameters={} ",
          eventEngineTask.getTaskParameters(), taskCU.getTaskParameters());

      eventEngineTask.setTaskParameters(taskCU.getTaskParameters());
      redeployTask = true;
    }

    if (handleSystemSpecificTaskUpdate(taskCU, eventEngineTask)) {
      redeployTask = true;
    }

    if (redeployTask) {
      // TODO trigger redeploy of esper task
    }

    EventEngineTask eventEngineTaskUpdated = eventEngineTaskRepository.save(eventEngineTask);
    taskSuccess.tags(
        MetricTags.OPERATION_METRIC_TAG, MetricTagValues.UPDATE_OPERATION,
        MetricTags.OBJECT_TYPE_METRIC_TAG, "task")
        .register(meterRegistry).increment();

    return eventEngineTaskUpdated;
  }

  /**
   * Helper method which calls the required type specific method.
   *
   * @param eventEngineTask
   * @param taskCU
   * @return True if any value was modified, otherwise false.
   */
  private boolean handleSystemSpecificTaskUpdate(TaskCU taskCU, EventEngineTask eventEngineTask) {
    if (eventEngineTask instanceof SalusEventEngineTask && taskCU instanceof SalusTaskCU) {
      return handleSalusTaskUpdate(taskCU, eventEngineTask);
    } else {
      return handleGenericTaskUpdate(taskCU, eventEngineTask);
    }
  }

  /**
   * Determines if any generic task type parameters have changed and updates them if so.
   *
   * @param taskCU
   * @param eventEngineTask
   * @return True if any value was modified, otherwise false.
   */
  private boolean handleGenericTaskUpdate(TaskCU taskCU, EventEngineTask eventEngineTask) {
    if (!(taskCU instanceof GenericTaskCU && eventEngineTask instanceof GenericEventEngineTask)) {
      throw new IllegalArgumentException(
          String.format("Invalid fields provided for '%s' task", eventEngineTask.getMonitoringSystem()));
    }

    GenericTaskCU genericCU = (GenericTaskCU) taskCU;
    GenericEventEngineTask genericTask = (GenericEventEngineTask) eventEngineTask;

    if (!StringUtils.isEmpty(genericCU.getMeasurement()) &&
        !genericCU.getMeasurement().equals(genericTask.getMeasurement())) {

      log.info("changing measurement={} to updatedMeasurement={}",
          genericTask.getMeasurement(), genericCU.getMeasurement());

      genericTask.setMeasurement(genericCU.getMeasurement());
      return true;
    }
    return false;
  }

  /**
   * Determines if any salus specific parameters have changed and updates them if so.
   *
   * @param taskCU
   * @param eventEngineTask
   * @return True if any value was modified, otherwise false.
   */
  private boolean handleSalusTaskUpdate(TaskCU taskCU, EventEngineTask eventEngineTask) {
    SalusTaskCU salusCU = (SalusTaskCU) taskCU;
    SalusEventEngineTask salusTask = (SalusEventEngineTask) eventEngineTask;
    boolean updateRequired = false;

    if (salusCU.getMonitorType() != null && salusCU.getMonitorType() != salusTask.getMonitorType()) {
      salusTask.setMonitorType(salusCU.getMonitorType());
      updateRequired = true;
    }
    if (salusCU.getSelectorScope() != null && salusCU.getSelectorScope() != salusTask.getSelectorScope()) {
      salusTask.setSelectorScope(salusCU.getSelectorScope());
      updateRequired = true;
    }
    return updateRequired;
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
