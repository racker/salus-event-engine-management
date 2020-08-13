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

import static com.rackspace.salus.test.JsonTestUtils.readContent;

import com.rackspace.salus.event.manage.config.AppProperties;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Comparator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LogicalExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LogicalExpression.Operator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.TaskState;
import com.rackspace.salus.telemetry.model.CustomEvalNode;
import com.rackspace.salus.telemetry.model.DerivativeNode;
import com.rackspace.salus.telemetry.model.EvalNode;
import com.rackspace.salus.telemetry.model.MetricExpressionBase;
import com.rackspace.salus.telemetry.model.PercentageEvalNode;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@Import({TickScriptBuilder.class, KapacitorTaskIdGenerator.class, AppProperties.class})
public class TickScriptBuilderTest {

  @Autowired
  TickScriptBuilder tickScriptBuilder;

  @Test
  public void testBuildNumberThreshold() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildNumberThreshold.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(0);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors)
        .setCriticalStateDuration(5);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildStringThreshold() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildStringThreshold.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("code")
        .setComparator(Comparator.NOT_REGEX_MATCH)
        .setComparisonValue("true");

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Invalid code found");

    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors)
        .setCriticalStateDuration(3);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildBooleanThreshold() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildBooleanThreshold.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.EQUAL_TO)
        .setComparisonValue(true);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors)
        .setCriticalStateDuration(2);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildOnlyInfo() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildOnlyInfo.tick");

    ComparisonExpression infoExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(infoExpression)
        .setState(TaskState.INFO)
        .setMessage("Thresholds returned to normal");

    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setInfoStateDuration(5)
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);

  }

  @Test
  public void testBuildNoStateChangesOnly() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildNoStateChangesOnly.tick");

    ComparisonExpression infoExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(infoExpression)
        .setState(TaskState.INFO)
        .setMessage("Thresholds returned to normal");

    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("measurement", tp,
        "test-event-task",
        List.of(TickScriptBuilder.ID_PART_TASK_NAME),
        false);
    Assert.assertEquals(expectedString, script);

  }

  @Test
  public void testBuildMultipleExpressions() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildMultipleExpressions.tick");

    LogicalExpression critExpressionLogic = new LogicalExpression()
        .setOperator(Operator.OR)
        .setExpressions(List.of(
            new ComparisonExpression()
                .setValueName("field")
                .setComparator(Comparator.GREATER_THAN)
                .setComparisonValue(33),
            new ComparisonExpression()
                .setValueName("test")
                .setComparator(Comparator.LESS_THAN_OR_EQUAL_TO)
                .setComparisonValue(17)));

    ComparisonExpression critExpressionComp = new ComparisonExpression()
        .setValueName("new_field")
        .setComparator(Comparator.REGEX_MATCH)
        .setComparisonValue("my_value");

    ComparisonExpression warnExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(30);

    ComparisonExpression infoExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(20);

    List<StateExpression> stateExpressions = List.of(
        new StateExpression()
            .setExpression(critExpressionLogic)
            .setState(TaskState.CRITICAL)
            .setMessage("logical critical threshold hit"),
        new StateExpression()
            .setExpression(critExpressionComp)
            .setState(TaskState.CRITICAL)
            .setMessage("comparison critical threshold hit"),
        new StateExpression()
            .setExpression(warnExpression)
            .setState(TaskState.WARNING)
            .setMessage("warning threshold hit"),
        new StateExpression()
            .setExpression(infoExpression)
            .setState(TaskState.INFO)
            .setMessage("thresholds returned to normal"));

    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(stateExpressions)
        .setInfoStateDuration(1)
        .setWarningStateDuration(3)
        .setCriticalStateDuration(5)
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildNoLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildNoLabels.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setCriticalStateDuration(5);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildEmptySetOfLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildNoLabels.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(Collections.EMPTY_MAP)
        .setCriticalStateDuration(5);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildMultipleLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildMultipleLabels.tick");
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    labelSelectors.put("resource_metadata_env", "prod");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors)
        .setCriticalStateDuration(5);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBasicEvalExpression() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildBasicEvalNode.tick");

    List<String> operands1 = Arrays.asList("1.0", "field", "sigma(cpu,1)");
    EvalNode evalExpression1 = new CustomEvalNode()
        .setOperator("+")
        .setOperands(operands1)
        .setAs("as1");
    List<String> operands2 = Arrays.asList("2.0", "tag", "count(field2,1)");
    EvalNode evalExpression2 = new CustomEvalNode()
        .setOperator("-")
        .setOperands(operands2)
        .setAs("as2");

    List<MetricExpressionBase> evalExpressions = new LinkedList<>();
    evalExpressions.add(evalExpression1);
    evalExpressions.add(evalExpression2);
    EventEngineTaskParameters tp = new EventEngineTaskParameters().setCustomMetrics(evalExpressions);
    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testPercentExpression() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildPercentNode.tick");

    EvalNode percentExpression = new PercentageEvalNode()
        .setPart("metric1")
        .setTotal("metric2")
        .setAs("percentage");

    EventEngineTaskParameters tp = new EventEngineTaskParameters().setCustomMetrics(List.of(percentExpression));
    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testDerivative() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildDerivativeNode.tick");

    DerivativeNode derivativeNode = new DerivativeNode()
        .setMetric("metric")
        .setAs("rate_metric");

    EventEngineTaskParameters tp = new EventEngineTaskParameters().setCustomMetrics(List.of(derivativeNode));
    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testWindows() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildWindow.tick");
    List<String> windowFields = Arrays.asList("field1", "field2");
    EventEngineTaskParameters tp = new EventEngineTaskParameters().setWindowFields(windowFields).setWindowLength(8);
    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testNullConsecutiveCount() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testNullConsecutiveCount.tick");
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    labelSelectors.put("resource_metadata_env", "prod");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  /**
   * Builds a TickScript string that utilizes a combination of everything else tested in this file.
   */
  @Test
  public void testBuildComplexExpression() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildComplexExpression.tick");

    // Create all state expressions

    LogicalExpression critExpressionLogic = new LogicalExpression()
        .setOperator(Operator.AND)
        .setExpressions(List.of(
            new ComparisonExpression()
                .setValueName("field")
                .setComparator(Comparator.GREATER_THAN)
                .setComparisonValue(33),
            new ComparisonExpression()
                .setValueName("test")
                .setComparator(Comparator.NOT_EQUAL_TO)
                .setComparisonValue(17)));

    ComparisonExpression critExpressionComp = new ComparisonExpression()
        .setValueName("new_field")
        .setComparator(Comparator.REGEX_MATCH)
        .setComparisonValue("my_value");

    ComparisonExpression warnExpression1 = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(30);

    ComparisonExpression warnExpression2 = new ComparisonExpression()
        .setValueName("false")
        .setComparator(Comparator.EQUAL_TO)
        .setComparisonValue(false);

    ComparisonExpression infoExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(20);

    List<StateExpression> stateExpressions = List.of(
        new StateExpression()
            .setExpression(critExpressionLogic)
            .setState(TaskState.CRITICAL)
            .setMessage("logical critical threshold hit"),
        new StateExpression()
            .setExpression(critExpressionComp)
            .setState(TaskState.CRITICAL)
            .setMessage("comparison critical threshold hit"),
        new StateExpression()
            .setExpression(warnExpression1)
            .setState(TaskState.WARNING)
            .setMessage("warning threshold hit"),
        new StateExpression()
            .setExpression(warnExpression2)
            .setState(TaskState.WARNING)
            .setMessage("warning threshold 2 hit"),
        new StateExpression()
            .setExpression(infoExpression)
            .setState(TaskState.INFO)
            .setMessage("thresholds returned to normal"));


    // Create all custom metrics

    EvalNode percentExpression = new PercentageEvalNode()
        .setPart("metric1")
        .setTotal("metric2")
        .setAs("percent");

    DerivativeNode derivativeNode = new DerivativeNode()
        .setMetric("testVal")
        .setDuration(Duration.ofSeconds(137))
        .setAs("new_rate");

    List<String> operands1 = Arrays.asList("20", "sqrt(number)");
    EvalNode evalExpression1 = new CustomEvalNode()
        .setOperator("-")
        .setOperands(operands1)
        .setAs("sqrt_val");

    List<String> operands2 = Arrays.asList("min(field1, field2)", "max(field3, field4)");
    EvalNode evalExpression2 = new CustomEvalNode()
        .setOperator("+")
        .setOperands(operands2)
        .setAs("combined_vals");

    List<MetricExpressionBase> customMetrics = List.of(
        percentExpression,
        derivativeNode,
        evalExpression1,
        evalExpression2);


    // Set labelSelector
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");


    // Build taskParameters

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(stateExpressions)
        .setCustomMetrics(customMetrics)
        .setInfoStateDuration(7)
        .setWarningStateDuration(null)
        .setCriticalStateDuration(2)
        .setLabelSelector(labelSelectors)
        .setWindowFields(List.of("wField1", "wField2"))
        .setWindowLength(12);

    String script = tickScriptBuilder.build("new_measurement", tp);

    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testAlertMessage() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testAddAlertMessageToKapacitor.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setValueName("field")
        .setComparator(Comparator.GREATER_THAN)
        .setComparisonValue(0);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setMessageTemplate("The CPU usage was too high")
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors)
        .setCriticalStateDuration(5);


    String script = tickScriptBuilder.build("measurement", tp);
    Assert.assertEquals(expectedString, script);
  }
}