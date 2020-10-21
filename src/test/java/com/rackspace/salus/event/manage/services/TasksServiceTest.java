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
 *
 */

package com.rackspace.salus.event.manage.services;

import static java.util.Collections.singletonMap;

import com.rackspace.salus.event.manage.config.DatabaseConfig;
import com.rackspace.salus.event.manage.model.GenericTaskCU;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Comparator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.entities.subtype.GenericEventEngineTask;
import com.rackspace.salus.telemetry.repositories.EventEngineTaskRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.transaction.Transactional;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    TasksService.class,
    TaskGenerator.class,
    DatabaseConfig.class
})
@AutoConfigureDataJpa
@EnableTestContainersDatabase
@Import({SimpleMeterRegistry.class})
public class TasksServiceTest {

  @Autowired
  TasksService tasksService;

  @Autowired
  TaskGenerator taskGenerator;

  @MockBean
  TaskPartitionIdGenerator partitionIdGenerator;

  @Autowired
  EventEngineTaskRepository eventEngineTaskRepository;

  @After
  public void tearDown() throws Exception {
    eventEngineTaskRepository.deleteAll();
  }

  @Test
  public void testCreate_success() throws IOException {

  }


  @Test
  public void testDeleteTask_success() {

  }

  @Test
  public void testDeleteTask_missingTask() {

  }

  @Test
  public void testDeleteTask_tenantMismatch() {

  }

  @Test
  public void testDeleteAllTasksForTenant() {

  }

  @Test
  @Transactional
  public void testUpdate_update_name() throws IOException {

  }

  @Transactional
  @Test
  public void testUpdate_update_measurementAndTaskParameters() throws IOException {

  }

  private void saveTask(UUID taskDbId) {
    final EventEngineTask eventEngineTask = new GenericEventEngineTask()
        .setMeasurement("cpu")
        .setId(taskDbId)
        .setName("task-1")
        .setTenantId("t-1")
        .setTaskParameters(new EventEngineTaskParameters())
        .setMonitoringSystem("SALUS")
        .setPartition(0);
    eventEngineTaskRepository.save(eventEngineTask);
  }

  private static GenericTaskCU buildCreateTask() {
    return (GenericTaskCU) new GenericTaskCU()
        .setMeasurement("cpu")
        .setName("task-1")
        .setTaskParameters(
            new EventEngineTaskParameters()
                .setLabelSelector(
                    singletonMap("agent_environment", "localdev")
                )
                .setCriticalStateDuration(5)
                .setStateExpressions(List.of(
                    new StateExpression()
                        .setExpression(
                            new ComparisonExpression()
                                .setValueName("usage_user")
                                .setComparator(Comparator.GREATER_THAN)
                                .setComparisonValue(75)
                        )
                    )
                ));
  }

  private static EventEngineTask buildEventEngineTask()  {
    UUID uuid = UUID.randomUUID();
    final GenericTaskCU taskIn = buildCreateTask();

    return new GenericEventEngineTask()
        .setMeasurement(taskIn.getMeasurement())
        .setMonitoringSystem("SALUS")
        .setId(uuid)
        .setTenantId("t-1")
        .setName(taskIn.getName())
        .setTaskParameters(taskIn.getTaskParameters())
        .setPartition(0);
  }
}