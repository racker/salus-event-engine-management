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

import com.rackspace.salus.event.manage.model.TaskParameters;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@Import({TickScriptBuilder.class, TaskIdGenerator.class})
@DataJpaTest
public class TickScriptBuilderTest {

  @Autowired
  TickScriptBuilder tickScriptBuilder;

  @Test
  public void testBuild() {
    String expectedString = "stream\n  |from()\n    .measurement('measurement')\n" +
            "    .groupBy('resourceId')\n  |alert()\n    .stateChangesOnly()\n" +
            "    .id('tenant:{{index .Tags \"system.resourceId\"}}:measurement:field')\n" +
            "    .details('task={{.TaskName}}')\n    .crit(lambda: \"field\" >= 33)\n    .topic('events')";

    TaskParameters tp = new TaskParameters().setComparator(">=").setField("field").setThreshold(33);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(script, expectedString);


  }

}