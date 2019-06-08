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

import static com.rackspace.salus.test.JsonTestUtils.readContent;
import com.rackspace.salus.event.manage.model.EvalExpression;
import com.rackspace.salus.event.manage.model.Expression;
import com.rackspace.salus.event.manage.model.TaskParameters;
import com.rackspace.salus.event.manage.model.TaskParameters.LevelExpression;
import java.io.IOException;
import java.util.HashMap;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
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
import org.springframework.util.FileCopyUtils;


@RunWith(SpringRunner.class)
@Import({TickScriptBuilder.class, TaskIdGenerator.class})
public class TickScriptBuilderTest {

  @Autowired
  TickScriptBuilder tickScriptBuilder;

  private static String readContent(String resource) throws IOException {
    try (InputStream in = new ClassPathResource(resource).getInputStream()) {
      return FileCopyUtils.copyToString(new InputStreamReader(in));
    }
  }

  @Test
  public void testBuild() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuild.tick");

    LevelExpression critExpression = new LevelExpression();
    critExpression.setConsecutiveCount(5)
        .setExpression(new Expression()
            .setComparator(">")
            .setField("field")
            .setThreshold(33));
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    TaskParameters tp = new TaskParameters()
        .setCritical(critExpression)
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);

  }

  @Test
  public void testBuildOnlyInfo() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildOnlyInfo.tick");

    LevelExpression infoExpression = new LevelExpression();
    infoExpression.setConsecutiveCount(5)
        .setExpression(new Expression()
            .setComparator(">")
            .setField("field")
            .setThreshold(33));
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    TaskParameters tp = new TaskParameters()
        .setInfo(infoExpression)
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);

  }

  @Test
  public void testBuildMultipleExpressions() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildMultipleExpressions.tick");

    LevelExpression critExpression = new LevelExpression();
    critExpression.setConsecutiveCount(5)
        .setExpression(new Expression()
            .setComparator(">")
            .setField("field")
            .setThreshold(33));

    LevelExpression warnExpression = new LevelExpression();
    warnExpression.setConsecutiveCount(3)
        .setExpression(new Expression()
            .setComparator(">")
            .setField("field")
            .setThreshold(33));

    LevelExpression infoExpression = new LevelExpression();
    infoExpression.setConsecutiveCount(1)
        .setExpression(new Expression()
            .setComparator(">")
            .setField("field")
            .setThreshold(20));
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    TaskParameters tp = new TaskParameters()
        .setCritical(critExpression)
        .setWarning(warnExpression)
        .setInfo(infoExpression)
        .setLabelSelector(labelSelectors);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);

  }

  @Test
  public void testBuildNoLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildNoLabels.tick");

    LevelExpression critExpression = new LevelExpression();
    critExpression.setConsecutiveCount(5)
        .setExpression(new Expression()
            .setComparator(">")
            .setField("field")
            .setThreshold(33));
    TaskParameters tp = new TaskParameters()
        .setCritical(critExpression);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);

  }

  @Test
  public void testBuildMultipleLabels() throws IOException {
    String expectedString = readContent("/TickScriptBuilderTest/testBuildMultipleLabels.tick");
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("resource_metadata_os", "linux");
    labelSelectors.put("resource_metadata_env", "prod");

    LevelExpression critExpression = new LevelExpression();
    critExpression.setConsecutiveCount(5)
        .setExpression(new Expression()
            .setComparator(">")
            .setField("field")
            .setThreshold(33));
    TaskParameters tp = new TaskParameters()
        .setCritical(critExpression)
        .setLabelSelector(labelSelectors);

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
    TaskParameters tp = new TaskParameters().setEvalExpressions(evalExpressions);
    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(expectedString, script);

  }
}