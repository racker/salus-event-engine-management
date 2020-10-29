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
 */

package com.rackspace.salus.event.manage.services;

import com.rackspace.salus.event.manage.model.GenericTaskCU;
import com.rackspace.salus.event.manage.model.SalusTaskCU;
import com.rackspace.salus.event.manage.model.TaskCU;
import com.rackspace.salus.event.manage.web.model.EventEngineTaskDTO;
import com.rackspace.salus.event.manage.web.model.GenericEventEngineTaskDTO;
import com.rackspace.salus.event.manage.web.model.SalusEventEngineTaskDTO;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.subtype.GenericEventEngineTask;
import com.rackspace.salus.telemetry.entities.subtype.SalusEventEngineTask;
import org.springframework.stereotype.Component;

@Component
public class TaskGenerator {

  private final TaskPartitionIdGenerator partitionIdGenerator;

  public TaskGenerator(
      TaskPartitionIdGenerator partitionIdGenerator) {
    this.partitionIdGenerator = partitionIdGenerator;
  }

  public EventEngineTask createTask(String tenantId, TaskCU in) {
    EventEngineTask task;
    if (in instanceof SalusTaskCU) {
      task = createSalusTask(tenantId, (SalusTaskCU) in);
    } else {
      task = createGenericTask(tenantId, (GenericTaskCU) in);
    }

    int partition = partitionIdGenerator.getPartitionForTask(task);
    task.setPartition(partition);

    return task;
  }

  public EventEngineTask updatePartition(EventEngineTask task) {
    int partition = partitionIdGenerator.getPartitionForTask(task);
    task.setPartition(partition);
    return task;
  }

  private EventEngineTask createSalusTask(String tenantId, SalusTaskCU in) {
    return new SalusEventEngineTask()
        .setMonitorType(in.getMonitorType())
        .setMonitorScope(in.getMonitorScope())
        .setTenantId(tenantId)
        .setName(in.getName())
        .setMonitoringSystem(in.getMonitoringSystem())
        .setTaskParameters(in.getTaskParameters());
  }
  private EventEngineTask createGenericTask(String tenantId, GenericTaskCU in) {
    return new GenericEventEngineTask()
        .setMeasurement(in.getMeasurement())
        .setTenantId(tenantId)
        .setName(in.getName())
        .setMonitoringSystem(in.getMonitoringSystem())
        .setTaskParameters(in.getTaskParameters());
  }

  public static EventEngineTaskDTO generateDto(EventEngineTask engineTask) {
    if (engineTask instanceof SalusEventEngineTask) {
      return new SalusEventEngineTaskDTO((SalusEventEngineTask) engineTask);
    }
    return new GenericEventEngineTaskDTO((GenericEventEngineTask) engineTask);
  }
}
