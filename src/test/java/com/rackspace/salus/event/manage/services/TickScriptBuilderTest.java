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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.event.discovery.DiscoveryProperties;
import com.rackspace.salus.event.discovery.DiscoveryServiceModule;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.model.TaskParameters;
import com.samskivert.mustache.Escapers;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@SpringBootTest
@Import({TickScriptBuilder.class})
public class TickScriptBuilderTest {
@MockBean
EventEnginePicker ep;

  @Autowired
  TickScriptBuilder tickScriptBuilder;

  @Test
  public void testBuild() throws Exception {
    String expectedString = "stream\n  |from()\n    .measurement('measurement')\n" +
            "    .groupBy('resourceId')\n  |alert()\n    .stateChangesOnly()\n" +
            "    .id('tenant:{{index .Tags \"system.resourceId\"}}:measurement:field')\n" +
            "    .details('task={{.TaskName}}')\n    .crit(lambda: \"field\" >= 33)\n    .topic('events')";

    TaskParameters tp = new TaskParameters().setComparator(">=").setField("field").setThreshold(33);

    String script = tickScriptBuilder.build("tenant", "measurement", tp);
    Assert.assertEquals(script, expectedString);


  }

}