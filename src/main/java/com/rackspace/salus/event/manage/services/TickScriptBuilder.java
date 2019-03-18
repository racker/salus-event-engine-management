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

import com.rackspace.salus.event.manage.model.scenarios.Falling;
import com.rackspace.salus.event.manage.model.scenarios.Rising;
import com.rackspace.salus.event.manage.model.scenarios.Scenario;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStreamReader;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class TickScriptBuilder {

  private final Template taskTemplate;
  private final TaskIdGenerator taskIdGenerator;

  @Autowired
  public TickScriptBuilder(TaskIdGenerator taskIdGenerator,
                           @Value("classpath:templates/task.mustache") Resource taskTemplateResource)
      throws IOException {

    final Compiler mustacheCompiler = Mustache.compiler().withEscaper(Escapers.NONE);
    try (InputStreamReader taskTemplateReader = new InputStreamReader(
        taskTemplateResource.getInputStream())) {
      taskTemplate = mustacheCompiler.compile(taskTemplateReader);
    }

    this.taskIdGenerator = taskIdGenerator;
  }

  public String build(String tenantId, String measurement, Scenario scenario) {

    if (scenario instanceof Rising) {
      return build(tenantId, measurement, ((Rising) scenario));
    } else if (scenario instanceof Falling) {
      return build(tenantId, measurement, ((Falling) scenario));
    } else {
      throw new IllegalArgumentException("Unsupport scenario type: " + scenario.getClass());
    }
  }

  private String build(String tenantId, String measurement, Rising scenario) {

    return taskTemplate.execute(TaskContext.builder()
        .alertId(taskIdGenerator.generateAlertId(tenantId, measurement, scenario.getField()))
        .measurement(measurement)
        .details("task={{.TaskName}}")
        .critExpression(String.format("\"%s\" > %s", scenario.getField(), scenario.getThreshold()))
        .build());
  }

  private String build(String tenantId, String measurement, Falling scenario) {

    return taskTemplate.execute(TaskContext.builder()
        .alertId(taskIdGenerator.generateAlertId(tenantId, measurement, scenario.getField()))
        .measurement(measurement)
        .details("task={{.TaskName}}")
        .critExpression(String.format("\"%s\" < %s", scenario.getField(), scenario.getThreshold()))
        .build());
  }

  @Data @Builder
  public static class TaskContext {
    String measurement;
    String alertId;
    String critExpression;
    @Default
    String details = "";
    @Default
    String groupBy = "resourceId";
  }
}
