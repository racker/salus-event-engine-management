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

package com.rackspace.salus.event.manage.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

@ConfigurationProperties("salus.event-engine-management.test-event-task")
@Component
@Data
public class TestEventTaskProperties {

  /**
   * The amount of time to allow for results of a test-event-task to be processed.
   */
  @DurationUnit(ChronoUnit.SECONDS)
  @NotNull
  Duration endToEndTimeout = Duration.ofSeconds(30);

  /**
   * References the topic name of the deployed kafka event handler for test-event-tasks.
   * This is different than the kafka topic where the events are produced.
   */
  String eventHandlerTopic = "test-event-task";
}
