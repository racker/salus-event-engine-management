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
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.EvalExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LevelExpression;
import com.rackspace.salus.telemetry.validators.EvalExpressionValidator;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
  public TickScriptBuilder(KapacitorTaskIdGenerator kapacitorTaskIdGenerator,
                           @Value("classpath:templates/task.mustache") Resource taskTemplateResource)
      throws IOException {

    final Compiler mustacheCompiler = Mustache.compiler().withEscaper(Escapers.NONE);
    try (InputStreamReader taskTemplateReader = new InputStreamReader(
        taskTemplateResource.getInputStream())) {
      taskTemplate = mustacheCompiler.compile(taskTemplateReader);
    }
  }

  public String build(String tenantId, String measurement, EventEngineTaskParameters taskParameters) {
    boolean labelsAvailable = false;
    if(taskParameters.getLabelSelector() != null && !taskParameters.getLabelSelector().isEmpty()) {
      labelsAvailable = true;

    }else {
      taskParameters.setLabelSelector(Collections.EMPTY_MAP);
    }

    return taskTemplate.execute(TaskContext.builder()
        .labels(!taskParameters.getLabelSelector().isEmpty() ? taskParameters.getLabelSelector().entrySet() : null)
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
        .joinedEvals(joinEvals(taskParameters.getEvalExpressions()))
        .joinedAs(joinAs(taskParameters.getEvalExpressions()))
        .windowLength(taskParameters.getWindowLength())
        .windowFields(taskParameters.getWindowFields())
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

  private Boolean isValidRealNumber(String operand) {
    return Pattern.matches("^[-+]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$", operand);
  }

  private String normalize(String operand) {
    if (isValidRealNumber(operand)) {
      return operand;
    }

    Matcher matcher = Pattern.compile(EvalExpressionValidator.functionRegex).matcher(operand);

    //  if operand is not a function call, double quote it
    if (!matcher.matches()) {
      // operand doesn't contain function, and thus is a tag/field name requiring double quotes
      return "\"" + operand + "\"";
    }

    // Operand is function call, so split out the function parameters, double quoting the tag/fields
    String parameters = Arrays.stream(matcher.group(2).split(","))
        .map(String::trim)
        .map(p -> isValidRealNumber(p) ? p : "\"" + p + "\"")
        .collect(Collectors.joining(", "));

    return matcher.group(1) + "(" + parameters + ")";
  }
  public String createLambda(EvalExpression evalExpression) {
    List<String> normalizedOperands = evalExpression.getOperands().stream()
            .map(this::normalize)
            .collect(Collectors.toList());
    
    return "lambda: " + normalizedOperands.stream()
            .collect(Collectors.joining(" " + evalExpression.getOperator() + " "));
  }

  public String joinEvals(List<EvalExpression> evalExpressionList) {
    if (evalExpressionList == null) {
      return null;
    }
    return evalExpressionList.stream()
            .map(this::createLambda)
            .collect(Collectors.joining(", "));
  }

  public String joinAs(List<EvalExpression> evalExpressionList) {
    if (evalExpressionList == null) {
      return null;
    }
    return evalExpressionList.stream()
            .map(evalExpression -> "'" + evalExpression.getAs() + "'")
            .collect(Collectors.joining(", "));
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
    List<String> windowFields = null;
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
    String joinedEvals;
    String joinedAs;
  }
}
