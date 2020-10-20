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

import com.rackspace.monplat.protocol.UmbKafkaPartitioner;
import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.subclass.GenericEventEngineTask;
import com.rackspace.salus.telemetry.entities.subclass.SalusEventEngineTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TaskPartitionIdGenerator {

  private final UmbKafkaPartitioner umbKafkaPartitioner;
  private final KafkaTopicProperties kafkaTopicProperties;

  @Autowired
  public TaskPartitionIdGenerator(
      KafkaTopicProperties kafkaTopicProperties) {
    this.umbKafkaPartitioner = new UmbKafkaPartitioner();
    this.kafkaTopicProperties = kafkaTopicProperties;
  }

  public int getPartitionForTask(EventEngineTask task) {
    return umbKafkaPartitioner.getPartitionForKeyBytes(
        getKeyBytesForTask(task),
        kafkaTopicProperties.getMetricsTopicPartitions());
  }


  private byte[] getKeyBytesForTask(EventEngineTask task) {
    if (task instanceof SalusEventEngineTask) {
      return getSalusMetricsKeyFromTask((SalusEventEngineTask) task).getBytes();
    }
    return getGenericMetricsKeyFromTask((GenericEventEngineTask) task).getBytes();

  }

  /**
   * This must remain in sync with the fields used to generate the partition for salus
   * metrics in umb.
   */
  private String getSalusMetricsKeyFromTask(SalusEventEngineTask task) {
    return String.join(":",
        task.getTenantId(),
        task.getMonitorType().name(),
        task.getSelectorScope().name());
  }

  private String getGenericMetricsKeyFromTask(GenericEventEngineTask task) {
    return String.join(":",
        task.getTenantId(),
        task.getMeasurement());
  }
}
