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

import static com.rackspace.salus.telemetry.messaging.KafkaMessageKeyBuilder.buildMessageKey;

import com.rackspace.salus.common.errors.RuntimeKafkaException;
import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.telemetry.messaging.TaskChangeEvent;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TaskEventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final KafkaTopicProperties properties;

  @Autowired
  public TaskEventProducer(
      KafkaTemplate<String, Object> kafkaTemplate,
      KafkaTopicProperties properties) {
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
  }

  public void sendTaskChangeEvent(TaskChangeEvent event) {
    final String topic = properties.getTaskChanges();

    log.debug("Sending taskChangeEvent={} on topic={}", event, topic);
    try {
      kafkaTemplate.send(topic, buildMessageKey(event), event).get();
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error sending task change event={}", event, e);
      throw new RuntimeKafkaException(e);
    }
  }
}
