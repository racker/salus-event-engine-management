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

import com.rackspace.salus.event.common.Tags;
import com.rackspace.salus.event.manage.model.TaskParameters;
import com.rackspace.salus.event.manage.model.TaskParameters.LevelExpression;
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


@Component
public class TickScriptBuilder {

  private final Template taskTemplate;

  @Autowired
  public TickScriptBuilder(TaskIdGenerator taskIdGenerator,
                           @Value("classpath:templates/task.mustache") Resource taskTemplateResource)
      throws IOException {

    final Compiler mustacheCompiler = Mustache.compiler().withEscaper(Escapers.NONE);
    try (InputStreamReader taskTemplateReader = new InputStreamReader(
        taskTemplateResource.getInputStream())) {
      taskTemplate = mustacheCompiler.compile(taskTemplateReader);
    }
  }

  public String build(String tenantId, String measurement, TaskParameters taskParameters) {
    boolean labelsAvailable = false;
    if(taskParameters.getLabelSelector() != null)
      labelsAvailable = true;
    return taskTemplate.execute(TaskContext.builder()
        .labels(taskParameters.getLabelSelector() != null ? taskParameters.getLabelSelector().entrySet() : null)
        .alertId(String.join(":",
            "{{ .TaskName }}",
            "{{ .Group }}"
            ))
        .labelsAvailable(labelsAvailable)
        .measurement(measurement)
        .details("task={{.TaskName}}")
        .critExpression(buildTICKExpression(taskParameters.getCritical()))
        .infoExpression(buildTICKExpression(taskParameters.getInfo()))
        .warnExpression(buildTICKExpression(taskParameters.getWarning()))
        .infoCount(
          buildTICKExpression(taskParameters.getInfo(), "\"info_count\" >= %d"))
        .warnCount(
          buildTICKExpression(taskParameters.getWarning(), "\"warn_count\" >= %d"))
        .critCount(
          buildTICKExpression(taskParameters.getCritical(), "\"crit_count\" >= %d"))
        .flappingDetection(taskParameters.isFlappingDetection())
        .build());
  }

  public String buildTICKExpression(LevelExpression expression) {
    return expression != null ? String.format("\"%s\" %s %s", expression.getExpression().getField(),
        expression.getExpression().getComparator(),
        expression.getExpression().getThreshold()) :
        null;
  }

  public String buildTICKExpression(LevelExpression consecutiveCount, String formatString) {
    return consecutiveCount != null ? String.format(formatString, consecutiveCount.getConsecutiveCount()) :
        null;
  }

  @Data @Builder
  public static class TaskContext {
    Set<Map.Entry<String, String>> labels;
    boolean labelsAvailable;
    boolean flappingDetection;
    String measurement;
    String alertId;
    String critCount;
    String warnCount;
    String infoCount;
    String critExpression;
    String warnExpression;
    String infoExpression;
    @Default
    Integer windowLength = null;
    @Default
    String windowField = null;
    @Default
    float flappingLower = .25f;
    @Default
    float flappingUpper = .5f;
    @Default
    int history = 21;
    @Default
    String details = "";
    @Default
    String groupBy = Tags.RESOURCE_ID;
  }
}
