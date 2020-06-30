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
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.EvalExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.TaskState;
import java.io.IOException;
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
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(33);

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

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildStringThreshold() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildStringThreshold.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setMetricName("code")
        .setComparator("!~")
        .setComparisonValue("[2-4]\\d\\d");

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

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildOnlyInfo() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildOnlyInfo.tick");

    ComparisonExpression infoExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
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

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);

  }

  @Test
  public void testBuildMultipleExpressions() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildMultipleExpressions.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(33);

    ComparisonExpression warnExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(30);

    ComparisonExpression infoExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(20);

    List<StateExpression> stateExpressions = List.of(
        new StateExpression()
            .setExpression(critExpression)
            .setState(TaskState.CRITICAL)
            .setMessage("critical threshold hit"),
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

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildNoLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildNoLabels.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setCriticalStateDuration(5);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildEmptySetOfLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildNoLabels.tick");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(Collections.EMPTY_MAP)
        .setCriticalStateDuration(5);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testBuildMultipleLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildMultipleLabels.tick");
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    labelSelectors.put("resource_metadata_env", "prod");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors)
        .setCriticalStateDuration(5);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testEvalExpression() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildEval.tick");

    List<String> operands1 = Arrays.asList("1.0", "field", "sigma(cpu,1)");
    EvalExpression evalExpression1 = new EvalExpression().setAs("as1").setOperator("+")
        .setOperands(operands1);
    List<String> operands2 = Arrays.asList("2.0", "tag", "count(field2,1)");
    EvalExpression evalExpression2 = new EvalExpression().setAs("as2").setOperator("-")
        .setOperands(operands2);

    List<EvalExpression> evalExpressions = new LinkedList<>();
    evalExpressions.add(evalExpression1);
    evalExpressions.add(evalExpression2);
    EventEngineTaskParameters tp = new EventEngineTaskParameters().setEvalExpressions(evalExpressions);
    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testWindows() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildWindow.tick");
    List<String> windowFields = Arrays.asList("field1", "field2");
    EventEngineTaskParameters tp = new EventEngineTaskParameters().setWindowFields(windowFields).setWindowLength(8);
    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);
  }

  @Test
  public void testNullConsecutiveCount() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testNullConsecutiveCount.tick");
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    labelSelectors.put("resource_metadata_env", "prod");

    ComparisonExpression critExpression = new ComparisonExpression()
        .setMetricName("field")
        .setComparator(">")
        .setComparisonValue(33);

    StateExpression stateExpression = new StateExpression()
        .setExpression(critExpression)
        .setState(TaskState.CRITICAL)
        .setMessage("Field is more than threshold");

    EventEngineTaskParameters tp = new EventEngineTaskParameters()
        .setStateExpressions(List.of(stateExpression))
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);

  }
}