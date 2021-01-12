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
import com.rackspace.salus.event.manage.model.TaskCU;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.messaging.TaskChangeEvent;
import com.rackspace.salus.telemetry.model.NotFoundException;
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
  private final TaskEventProducer taskEventProducer;

  MeterRegistry meterRegistry;

  // metrics counters
  private final Counter.Builder taskSuccess;

  @Autowired
  public TasksService(MeterRegistry meterRegistry,
                      EventEngineTaskRepository eventEngineTaskRepository,
                      TaskEventProducer taskEventProducer) {

    this.eventEngineTaskRepository = eventEngineTaskRepository;
    this.meterRegistry = meterRegistry;
    this.taskEventProducer = taskEventProducer;

    taskSuccess = Counter.builder(MetricNames.SERVICE_OPERATION_SUCCEEDED)
        .tag(MetricTags.SERVICE_METRIC_TAG, "Tasks");
  }

  @Transactional
  public EventEngineTask createTask(String tenantId, TaskCU in) {

    final EventEngineTask eventEngineTask = new EventEngineTask()
        .setTenantId(tenantId)
        .setName(in.getName())
        .setMonitoringSystem(in.getMonitoringSystem())
        .setTaskParameters(in.getTaskParameters());

    EventEngineTask eventEngineTaskSaved = eventEngineTaskRepository.save(eventEngineTask);
    sendTaskChangeEvent(eventEngineTaskSaved);

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
  public void deleteTask(String tenantId, UUID id) {

    final EventEngineTask eventEngineTask = getTask(tenantId, id).orElseThrow(() ->
        new NotFoundException(String.format("No task found for %s on tenant %s",
            id, tenantId)));

    eventEngineTaskRepository.delete(eventEngineTask);
    sendTaskChangeEvent(eventEngineTask);

    taskSuccess.tags(
        MetricTags.OPERATION_METRIC_TAG, MetricTagValues.REMOVE_OPERATION,
        MetricTags.OBJECT_TYPE_METRIC_TAG, "task")
        .register(meterRegistry).increment();
  }

  public void deleteAllTasksForTenant(String tenant) {
    getTasks(tenant, Pageable.unpaged())
        .forEach(task -> deleteTask(tenant, task.getId()));
  }

  @Transactional
  public EventEngineTask updateTask(String tenantId, UUID uuid, TaskCU taskCU) {
    EventEngineTask eventEngineTask = getTask(tenantId, uuid).orElseThrow(() ->
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

    EventEngineTask eventEngineTaskUpdated = eventEngineTaskRepository.save(eventEngineTask);
    if (redeployTask) {
      sendTaskChangeEvent(eventEngineTask);
    }

    taskSuccess.tags(
        MetricTags.OPERATION_METRIC_TAG, MetricTagValues.UPDATE_OPERATION,
        MetricTags.OBJECT_TYPE_METRIC_TAG, "task")
        .register(meterRegistry).increment();

    return eventEngineTaskUpdated;
  }

  /**
   * Send task change event for a particular event task.
   * @param eventEngineTask The event task that has been modified.
   */
  private void sendTaskChangeEvent(EventEngineTask eventEngineTask) {
    taskEventProducer.sendTaskChangeEvent(
        new TaskChangeEvent()
            .setTenantId(eventEngineTask.getTenantId())
            .setTaskId(eventEngineTask.getId())
    );
  }
}