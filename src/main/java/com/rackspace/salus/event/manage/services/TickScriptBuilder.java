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

import com.rackspace.salus.event.manage.model.Expression;
import com.rackspace.salus.event.manage.model.TaskParameters;
import com.rackspace.salus.event.manage.model.TaskParameters.LevelExpression;
import com.rackspace.salus.telemetry.model.LabelNamespaces;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import static com.rackspace.salus.telemetry.model.LabelNamespaces.MONITORING_SYSTEM_METADATA;

@Component
public class TickScriptBuilder {

  private final Template taskTemplate;
  private final TaskIdGenerator taskIdGenerator;
  private LevelExpression levelExpression;

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

  public String build(String tenantId, String measurement,  TaskParameters taskParameters) {
    boolean labelsAvailable = false;
    if(taskParameters.getLabelSelector() != null)
      labelsAvailable = true;
    levelExpression = taskParameters.getInfo();
    return taskTemplate.execute(TaskContext.builder()
        .labels(taskParameters.getLabelSelector() != null ? taskParameters.getLabelSelector().entrySet() : null)
        .alertId(taskIdGenerator.generateAlertId(tenantId, measurement, taskParameters.getCritical().getExpression().getField()))
        .labelsAvailable(labelsAvailable)
        .measurement(measurement)
        .details("task={{.TaskName}}")
        .critExpression(buildTICKExpression(taskParameters.getCritical()))
        .infoExpression(buildTICKExpression(taskParameters.getInfo()))
        .warnExpression(buildTICKExpression(taskParameters.getWarning()))
        .infoCount(
            taskParameters.getInfo() != null ?
                String.format("\"info_count\" >= %d", taskParameters.getInfo().getConsecutiveCount())
                : null)
        .warnCount(
          taskParameters.getWarning() != null ?
            String.format("\"warn_count\" >= %d",taskParameters.getWarning().getConsecutiveCount())
            : null)
        .critCount(String.format("\"crit_count\" >= %d", taskParameters.getCritical().getConsecutiveCount()))
        .build());
  }

  public String buildTICKExpression(LevelExpression expression) {
    return expression != null ? String.format("\"%s\" %s %s", expression.getExpression().getField(),
        expression.getExpression().getComparator(),
        expression.getExpression().getThreshold()) :
        null;
  }

  @Data @Builder
  public static class TaskContext {
    Set<Map.Entry<String, String>> labels;
    boolean labelsAvailable;
    String measurement;
    String alertId;
    String critCount;
    String warnCount;
    String infoCount;
    String critExpression;
    String warnExpression;
    String infoExpression;
    @Default
    String details = "";
    @Default
    String groupBy = "resourceId";
  }
}
