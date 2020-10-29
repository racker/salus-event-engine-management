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

import static org.assertj.core.api.Assertions.assertThat;

import com.rackspace.monplat.protocol.UmbKafkaPartitioner;
import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.subtype.GenericEventEngineTask;
import com.rackspace.salus.telemetry.entities.subtype.SalusEventEngineTask;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.MonitoringSystem;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.InvalidRecordException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    TaskPartitionIdGenerator.class,
    KafkaTopicProperties.class,
    UmbKafkaPartitioner.class})
public class TaskPartitionIdGeneratorTest {

  @Autowired
  TaskPartitionIdGenerator taskPartitionIdGenerator;

  @Autowired
  KafkaTopicProperties kafkaTopicProperties;

  @Test
  public void testGetPartitionForTask_genericTask() {
    InvalidRecordException exception;
    GenericEventEngineTask eventEngineTask = (GenericEventEngineTask) new GenericEventEngineTask()
        .setMeasurement("myMeasurement")
        .setId(UUID.randomUUID())
        .setName("myName")
        .setTenantId("t-2000")
        .setTaskParameters(null)
        .setMonitoringSystem(MonitoringSystem.UIM);

    int id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask);

    assertThat(id).isEqualTo(41);

    // changing name doesn't affect anything
    eventEngineTask.setName("newName");
    id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask);
    assertThat(id).isEqualTo(41);

    // changing measurement does alter the partitionId
    eventEngineTask.setMeasurement("newMeasurement");
    id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask);
    assertThat(id).isEqualTo(27);

    // different tenant with same measurement also affects partitionId
    GenericEventEngineTask eventEngineTask2 = (GenericEventEngineTask) new GenericEventEngineTask()
        .setMeasurement("myMeasurement")
        .setId(UUID.randomUUID())
        .setName("myName")
        .setTenantId("t-3000")
        .setTaskParameters(null)
        .setMonitoringSystem(MonitoringSystem.UIM)
        .setPartition(0);

    id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask2);
    assertThat(id).isEqualTo(17);

    // changing any other fields doesn't alter partition
    eventEngineTask2
        .setId(UUID.randomUUID())
        .setMonitoringSystem(MonitoringSystem.SCOM)
        .setTaskParameters(new EventEngineTaskParameters())
        .setCreatedTimestamp(Instant.now())
        .setUpdatedTimestamp(Instant.EPOCH)
        .setPartition(12);

    assertThat(id).isEqualTo(17);
  }

  @Test
  public void testGetPartitionForTask_salusTask() {
    InvalidRecordException exception;
    SalusEventEngineTask eventEngineTask = (SalusEventEngineTask) new SalusEventEngineTask()
        .setMonitorType(MonitorType.http)
        .setMonitorScope(ConfigSelectorScope.REMOTE)
        .setId(UUID.randomUUID())
        .setName("myName")
        .setTenantId("t-2000")
        .setTaskParameters(null)
        .setMonitoringSystem(MonitoringSystem.UIM);

    int id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask);

    assertThat(id).isEqualTo(27);

    // changing name doesn't affect anything
    eventEngineTask.setName("newName");
    id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask);
    assertThat(id).isEqualTo(27);

    // same monitor with different scope does alter the partitionId
    eventEngineTask.setMonitorScope(ConfigSelectorScope.LOCAL);
    id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask);
    assertThat(id).isEqualTo(7);

    // changing monitor type also affects partitionId
    SalusEventEngineTask eventEngineTask2 = (SalusEventEngineTask) new SalusEventEngineTask()
        .setMonitorType(MonitorType.http)
        .setMonitorScope(ConfigSelectorScope.REMOTE)
        .setId(UUID.randomUUID())
        .setName("myName")
        .setTenantId("t-3000")
        .setTaskParameters(null)
        .setMonitoringSystem(MonitoringSystem.UIM);

    id = taskPartitionIdGenerator.getPartitionForTask(eventEngineTask2);
    assertThat(id).isEqualTo(25);

    // changing any other fields doesn't alter partition
    eventEngineTask2
        .setId(UUID.randomUUID())
        .setMonitoringSystem(MonitoringSystem.SALUS)
        .setTaskParameters(new EventEngineTaskParameters())
        .setCreatedTimestamp(Instant.now())
        .setUpdatedTimestamp(Instant.EPOCH)
        .setPartition(12);

    assertThat(id).isEqualTo(25);
  }
}
