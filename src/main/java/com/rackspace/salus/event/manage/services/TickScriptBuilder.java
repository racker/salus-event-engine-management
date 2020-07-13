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

import com.rackspace.salus.event.common.Tags;
import com.rackspace.salus.event.manage.config.AppProperties;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Expression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LogicalExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LogicalExpression.Operator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.TaskState;
import com.rackspace.salus.telemetry.model.MetricExpressionBase;
import com.rackspace.salus.telemetry.model.DerivativeNode;
import com.rackspace.salus.telemetry.model.EvalNode;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class TickScriptBuilder {

  public static final String ID_PART_TASK_NAME = "{{ .TaskName }}";
  public static final String ID_PART_GROUP = "{{ .Group }}";

  private final Template taskTemplate;
  private final AppProperties appProperties;

  @Autowired
  public TickScriptBuilder(AppProperties appProperties,
                           @Value("classpath:templates/task.mustache") Resource taskTemplateResource)
      throws IOException {
    this.appProperties = appProperties;

    final Compiler mustacheCompiler = Mustache.compiler().withEscaper(Escapers.NONE);
    try (InputStreamReader taskTemplateReader = new InputStreamReader(
        taskTemplateResource.getInputStream())) {
      taskTemplate = mustacheCompiler.compile(taskTemplateReader);
    }
  }

  public String build(String tenantId, String measurement,
                      EventEngineTaskParameters taskParameters) {
    return build(tenantId, measurement, taskParameters, appProperties.getEventHandlerTopic(),
        List.of(ID_PART_TASK_NAME, ID_PART_GROUP));
  }

  public String build(String tenantId,
                      String measurement,
                      EventEngineTaskParameters taskParameters,
                      String eventHandlerTopic,
                      List<String> idParts) {
    boolean labelsAvailable = false;
    if(taskParameters.getLabelSelector() != null && !taskParameters.getLabelSelector().isEmpty()) {
      labelsAvailable = true;

    } else {
      taskParameters.setLabelSelector(Collections.EMPTY_MAP);
    }


    Map<TaskState, List<StateExpression>> expressionsByState = taskParameters.getStateExpressions()
        .stream()
        .collect(Collectors.groupingBy(StateExpression::getState));

    return taskTemplate.execute(TaskContext.builder()
        .labels(!taskParameters.getLabelSelector().isEmpty() ? taskParameters.getLabelSelector().entrySet() : null)
        .alertId(String.join(":", idParts))
        .labelsAvailable(labelsAvailable)
        .measurement(measurement)
        .details("task={{.TaskName}}")
        .infoExpression(buildTICKExpression(expressionsByState.get(TaskState.INFO)))
        .warnExpression(buildTICKExpression(expressionsByState.get(TaskState.WARNING)))
        .critExpression(buildTICKExpression(expressionsByState.get(TaskState.CRITICAL)))
        .infoCount(taskParameters.getInfoStateDuration() != null ?
            String.format("\"info_count\" >= %d", taskParameters.getInfoStateDuration()): null)
        .warnCount(taskParameters.getWarningStateDuration() != null ?
            String.format("\"warn_count\" >= %d", taskParameters.getWarningStateDuration()): null)
        .critCount(taskParameters.getCriticalStateDuration() != null ?
            String.format("\"crit_count\" >= %d", taskParameters.getCriticalStateDuration()) : null)
        .flappingDetection(taskParameters.isFlappingDetection())
        .joinedEvals(joinEvals(taskParameters.getCustomMetrics()))
        .joinedAs(joinAs(taskParameters.getCustomMetrics()))
        .derivative(getDerivative(taskParameters.getCustomMetrics()))
        .windowLength(taskParameters.getWindowLength())
        .windowFields(taskParameters.getWindowFields())
        .eventHandlerTopic(eventHandlerTopic)
        .build());
  }

  public String buildTICKExpression(List<StateExpression> expressions) {
    if (expressions == null || expressions.isEmpty()) {
      return null;
    }

    return expressions.stream()
        .map(expr -> buildTICKExpression(expr.getExpression()))
        .collect(Collectors.joining(" " + Operator.OR.toString() + " "));
  }

  private String buildTICKExpression(Expression expression) {
    if (expression instanceof ComparisonExpression) {
      return buildTICKExpression((ComparisonExpression) expression);
    } else if (expression instanceof LogicalExpression) {
      return buildTICKExpression((LogicalExpression) expression);
    } else {
      log.error("Unknown expression type found", expression);
      return null;
    }
  }

  private String buildTICKExpression(LogicalExpression expression) {
    String logicalOperator = expression.getOperator().toString();

    return "("
        + expression.getExpressions().stream()
        .map(this::buildTICKExpression)
        .collect(Collectors.joining(" " + logicalOperator + " "))
        + ")";
  }

  private String buildTICKExpression(ComparisonExpression expression) {
    StringBuilder tickExpression = new StringBuilder("(");
    tickExpression.append("\"").append(expression.getMetricName()).append("\"");
    tickExpression.append(" ");
    tickExpression.append(expression.getComparator().getFriendlyName());
    tickExpression.append(" ");

    if (expression.getComparisonValue() instanceof Number) {
      tickExpression.append(expression.getComparisonValue());
    } else if (expression.getComparisonValue() instanceof Boolean) {
      tickExpression.append((Boolean) expression.getComparisonValue() ? "TRUE" : "FALSE");
    } else if (expression.getComparisonValue() instanceof String) {
      tickExpression.append("\"").append((String) expression.getComparisonValue()).append("\"");
    } else {
      log.error("Could not evaluate task comparison value from {}", expression.getComparisonValue());
      return null;
    }

    tickExpression.append(")");

    return tickExpression.toString();
  }

  public String joinEvals(List<MetricExpressionBase> customMetrics) {
    if (customMetrics == null) {
      return null;
    }
    String joinedEvals = customMetrics.stream()
        .filter(EvalNode.class::isInstance)
        .map(eval -> ((EvalNode) eval).getLambda())
        .collect(Collectors.joining(", "));

    if (joinedEvals.isBlank()) {
      return null;
    }
    return joinedEvals;
  }

  public String joinAs(List<MetricExpressionBase> customMetrics) {
    if (customMetrics == null) {
      return null;
    }
    String joinedAs = customMetrics.stream()
        .filter(EvalNode.class::isInstance)
        .map(eval -> "'" + ((EvalNode) eval).getAs() + "'")
        .collect(Collectors.joining(", "));

    if (joinedAs.isBlank()) {
      return null;
    }
    return joinedAs;
  }

  /**
   * Retrieves the first DerivativeNode seen in the list of custom metrics.
   *
   * Per https://github.com/influxdata/kapacitor/issues/2064 it appears that at most two of these
   * could be provided.  This method currently only handles one.
   *
   * @param customMetrics The list of custom metrics to filter DerivativeNodes from.
   * @return The first derivative node seen.
   */
  private DerivativeNode getDerivative(List<MetricExpressionBase> customMetrics) {
    if (customMetrics == null) {
      return null;
    }
    return (DerivativeNode) customMetrics.stream()
        .filter(DerivativeNode.class::isInstance)
        .findFirst()
        .orElse(null);
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
    DerivativeNode derivative;
    String eventHandlerTopic;
  }
}
