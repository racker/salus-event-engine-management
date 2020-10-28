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
import com.rackspace.salus.event.manage.errors.NotFoundException;
import com.rackspace.salus.event.manage.model.TaskCU;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.repositories.EventEngineTaskRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class TasksService {

  private final EventEngineTaskRepository eventEngineTaskRepository;

  MeterRegistry meterRegistry;

  // metrics counters
  private final Counter.Builder taskSuccess;

  @Autowired
  public TasksService(MeterRegistry meterRegistry, EventEngineTaskRepository eventEngineTaskRepository) {

    this.eventEngineTaskRepository = eventEngineTaskRepository;
    this.meterRegistry = meterRegistry;

    taskSuccess = Counter.builder(MetricNames.SERVICE_OPERATION_SUCCEEDED)
        .tag(MetricTags.SERVICE_METRIC_TAG, "Tasks");
  }

  @Transactional
  public EventEngineTask createTask(String tenantId, TaskCU in) {
    final EventEngineTask eventEngineTask = new EventEngineTask()
        .setTenantId(tenantId)
        .setMeasurement(in.getMeasurement())
        .setName(in.getName())
        .setTaskParameters(in.getTaskParameters());

    EventEngineTask eventEngineTaskSaved = eventEngineTaskRepository.save(eventEngineTask);
    taskSuccess.tags(
        MetricTags.OPERATION_METRIC_TAG, MetricTagValues.CREATE_OPERATION,
        MetricTags.OBJECT_TYPE_METRIC_TAG, "task")
        .register(meterRegistry).increment();

    // TODO send task change event to kafka

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

    eventEngineTaskRepository.delete(eventEngineTask);

    // TODO send task change event to kafka

    taskSuccess.tags(
        MetricTags.OPERATION_METRIC_TAG, MetricTagValues.REMOVE_OPERATION,
        MetricTags.OBJECT_TYPE_METRIC_TAG, "task")
        .register(meterRegistry).increment();
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
    if(!StringUtils.isEmpty(taskCU.getName()) && !eventEngineTask.getName().equals(taskCU.getName()))  {
      log.info("changing name={} to updatedName={} ",eventEngineTask.getName(), taskCU.getName());
      eventEngineTask.setName(taskCU.getName());
    }
    if(!StringUtils.isEmpty(taskCU.getMeasurement()) && !taskCU.getMeasurement().equals(eventEngineTask.getMeasurement())){
      log.info("changing measurement={} to updatedMeasurement={} ",eventEngineTask.getMeasurement(), taskCU.getMeasurement());
      eventEngineTask.setMeasurement(taskCU.getMeasurement());
      redeployTask = true;
    }
    if(taskCU.getTaskParameters() != null && !taskCU.getTaskParameters().equals(eventEngineTask.getTaskParameters())){
      log.info("changing task parameters={} to updated taskParameters={} ",eventEngineTask.getTaskParameters(), taskCU.getTaskParameters());
      eventEngineTask.setTaskParameters(taskCU.getTaskParameters());
      redeployTask = true;
    }

    if (redeployTask) {
      // TODO trigger redeploy of esper task
    }
    EventEngineTask eventEngineTaskUpdated = eventEngineTaskRepository.save(eventEngineTask);
    taskSuccess.tags(MetricTags.OPERATION_METRIC_TAG, MetricTagValues.UPDATE_OPERATION,MetricTags.OBJECT_TYPE_METRIC_TAG,"task").register(meterRegistry).increment();
    return eventEngineTaskUpdated;
  }
}
