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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rackspace.salus.event.manage.config.DatabaseConfig;
import com.rackspace.salus.event.manage.model.GenericTaskCU;
import com.rackspace.salus.event.manage.model.SalusTaskCU;
import com.rackspace.salus.event.manage.model.TaskCU;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Comparator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.entities.subtype.GenericEventEngineTask;
import com.rackspace.salus.telemetry.entities.subtype.SalusEventEngineTask;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import com.rackspace.salus.telemetry.model.MonitoringSystem;
import com.rackspace.salus.telemetry.model.NotFoundException;
import com.rackspace.salus.telemetry.repositories.EventEngineTaskRepository;
import com.rackspace.salus.test.EnableTestContainersDatabase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.transaction.Transactional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;

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

  @Before
  public void setup() {
    when(partitionIdGenerator.getPartitionForTask(any()))
        .thenReturn(6);
  }

  @After
  public void tearDown() throws Exception {
    eventEngineTaskRepository.deleteAll();
  }

  @Test
  public void testCreate_generic_success() {
    final GenericTaskCU taskIn = buildGenericCreateTask();

    final EventEngineTask created = tasksService.createTask("t-1", taskIn);

    assertThat(created).isNotNull();

    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isInstanceOf(GenericEventEngineTask.class);

    GenericEventEngineTask task = (GenericEventEngineTask) retrieved.get();
    // timestamps are returned with slightly less precision on the retrieval
    assertThat(task).isEqualToIgnoringGivenFields(created, "createdTimestamp", "updatedTimestamp");
    assertThat(task.getMonitoringSystem()).isEqualTo("UIM");
    assertThat(task.getMeasurement()).isEqualTo(taskIn.getMeasurement());
    assertThat(task.getPartition()).isEqualTo(6);

    verify(partitionIdGenerator).getPartitionForTask(created);
  }

  @Test
  public void testCreate_salus_success() {
    final SalusTaskCU taskIn = buildSalusCreateTask();

    final EventEngineTask created = tasksService.createTask("t-1", taskIn);

    assertThat(created).isNotNull();

    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isInstanceOf(SalusEventEngineTask.class);

    SalusEventEngineTask task = (SalusEventEngineTask) retrieved.get();
    // timestamps are returned with slightly less precision on the retrieval
    assertThat(task).isEqualToIgnoringGivenFields(created, "createdTimestamp", "updatedTimestamp");
    assertThat(task.getMonitoringSystem()).isEqualTo("SALUS");
    assertThat(task.getMonitorType()).isEqualTo(taskIn.getMonitorType());
    assertThat(task.getMonitorScope()).isEqualTo(taskIn.getMonitorScope());
    assertThat(task.getPartition()).isEqualTo(6);

    verify(partitionIdGenerator).getPartitionForTask(created);
  }

  @Test
  public void testDeleteTask_success() {
    EventEngineTask created = saveSalusTask();
    Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isPresent();


    tasksService.deleteTask(created.getTenantId(), created.getId());

    retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isEmpty();
  }

  @Test
  public void testDeleteTask_missingTask() {
    String tenantId = RandomStringUtils.randomAlphabetic(5);
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> {
      tasksService.deleteTask(tenantId, id);
    })
        .isInstanceOf(NotFoundException.class)
        .hasMessage(String.format("No task found for %s on tenant %s",
            id, tenantId));
  }

  @Test
  public void testDeleteTask_tenantMismatch() {
    String wrongTenantId = RandomStringUtils.randomAlphabetic(5);

    EventEngineTask created = saveSalusTask();
    Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isPresent();

    assertThatThrownBy(() -> {
      tasksService.deleteTask(wrongTenantId, created.getId());
    })
        .isInstanceOf(NotFoundException.class)
        .hasMessage(String.format("No task found for %s on tenant %s",
            created.getId(), wrongTenantId));
  }

  @Test
  public void testDeleteAllTasksForTenant() {
    saveSalusTask();
    saveSalusTask();

    Page<EventEngineTask> retrieved = eventEngineTaskRepository.findByTenantId(
        "t-1", Pageable.unpaged());
    assertThat(retrieved).hasSize(2);

    tasksService.deleteAllTasksForTenant("t-1");

    retrieved = eventEngineTaskRepository.findByTenantId("t-1", Pageable.unpaged());
    assertThat(retrieved).isEmpty();
  }

  @Test
  @Transactional
  public void testUpdate_update_name() {
    EventEngineTask saved = saveSalusTask();
    assertThat(saved.getPartition()).isEqualTo(0);

    TaskCU taskCU = new SalusTaskCU().setName("new task name");
    final EventEngineTask updated = tasksService.updateTask(saved.getTenantId(), saved.getId(), taskCU);

    assertThat(updated).isNotNull();
    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(updated.getId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().getName()).isEqualTo("new task name");
    assertThat(retrieved.get().getPartition()).isEqualTo(0); // partition is unchanged

    verify(partitionIdGenerator, never()).getPartitionForTask(saved);
  }

  @Transactional
  @Test
  public void testUpdate_update_generic_measurementAndTaskParameters() {
    EventEngineTask saved = saveGenericTask();
    assertThat(saved.getPartition()).isEqualTo(0);

    GenericTaskCU taskCU = buildGenericCreateTask();
    taskCU.setMeasurement("newMeasurement");
    taskCU.getTaskParameters().setCriticalStateDuration(15);

    final EventEngineTask updated = tasksService.updateTask(saved.getTenantId(), saved.getId(), taskCU);

    assertThat(updated).isNotNull();
    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(updated.getId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isInstanceOf(GenericEventEngineTask.class);

    GenericEventEngineTask task = (GenericEventEngineTask) retrieved.get();
    assertThat(task.getMeasurement()).isEqualTo("newMeasurement");
    assertThat(task.getTaskParameters().getCriticalStateDuration()).isEqualTo(15);
    assertThat(task.getPartition()).isEqualTo(6); // partition is updated

    verify(partitionIdGenerator).getPartitionForTask(saved);
  }

  /**
   * Testing an individual field that should trigger the partitionId to change
   */
  @Transactional
  @Test
  public void testUpdate_update_salus_monitorScope() {
    EventEngineTask saved = saveSalusTask();
    assertThat(saved.getPartition()).isEqualTo(0);

    SalusTaskCU taskCU = buildSalusCreateTask();
    taskCU.setMonitorScope(ConfigSelectorScope.REMOTE);

    final EventEngineTask updated = tasksService.updateTask(saved.getTenantId(), saved.getId(), taskCU);

    assertThat(updated).isNotNull();
    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(updated.getId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isInstanceOf(SalusEventEngineTask.class);

    SalusEventEngineTask task = (SalusEventEngineTask) retrieved.get();
    assertThat(task.getMonitorScope()).isEqualTo(ConfigSelectorScope.REMOTE); // changed
    assertThat(task.getMonitorType()).isEqualTo(MonitorType.mem); // unchanged
    assertThat(task.getPartition()).isEqualTo(6); // partition is updated

    verify(partitionIdGenerator).getPartitionForTask(saved);
  }

  /**
   * Testing an individual field that should trigger the partitionId to change
   */
  @Transactional
  @Test
  public void testUpdate_update_salus_monitorType() {
    EventEngineTask saved = saveSalusTask();
    assertThat(saved.getPartition()).isEqualTo(0);

    SalusTaskCU taskCU = buildSalusCreateTask();
    taskCU.setMonitorType(MonitorType.apache);

    final EventEngineTask updated = tasksService.updateTask(saved.getTenantId(), saved.getId(), taskCU);

    assertThat(updated).isNotNull();
    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(updated.getId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get()).isInstanceOf(SalusEventEngineTask.class);

    SalusEventEngineTask task = (SalusEventEngineTask) retrieved.get();
    assertThat(task.getMonitorScope()).isEqualTo(ConfigSelectorScope.LOCAL); // unchanged
    assertThat(task.getMonitorType()).isEqualTo(MonitorType.apache); // changed
    assertThat(task.getPartition()).isEqualTo(6); // partition is updated

    verify(partitionIdGenerator).getPartitionForTask(saved);
  }

  /**
   * Store an event task in sql.
   * Reuse values from buildSalusCreateTask to keep objects consistent.
   *
   * @return The saved task.
   */
  private EventEngineTask saveSalusTask() {
    final SalusTaskCU taskIn = buildSalusCreateTask();

    final EventEngineTask eventEngineTask = new SalusEventEngineTask()
        .setMonitorType(taskIn.getMonitorType())
        .setMonitorScope(taskIn.getMonitorScope())
        .setId(UUID.randomUUID())
        .setName(taskIn.getName())
        .setTenantId("t-1")
        .setTaskParameters(taskIn.getTaskParameters())
        .setMonitoringSystem(taskIn.getMonitoringSystem())
        .setPartition(0);
    return eventEngineTaskRepository.save(eventEngineTask);
  }

  /**
   * Store an event task in sql.
   * Reuse values from buildGenericCreateTask to keep objects consistent.
   *
   * @return The saved task.
   */
  private EventEngineTask saveGenericTask() {
    final GenericTaskCU taskIn = buildGenericCreateTask();

    final EventEngineTask eventEngineTask = new GenericEventEngineTask()
        .setMeasurement(taskIn.getMeasurement())
        .setId(UUID.randomUUID())
        .setName(taskIn.getName())
        .setTenantId("t-1")
        .setTaskParameters(taskIn.getTaskParameters())
        .setMonitoringSystem(taskIn.getMonitoringSystem())
        .setPartition(0);
    return eventEngineTaskRepository.save(eventEngineTask);
  }

  private static GenericTaskCU buildGenericCreateTask() {
    return (GenericTaskCU) new GenericTaskCU()
        .setMeasurement("cpu")
        .setMonitoringSystem(MonitoringSystem.UIM)
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

  private static SalusTaskCU buildSalusCreateTask() {
    return (SalusTaskCU) new SalusTaskCU()
        .setMonitorType(MonitorType.mem)
        .setMonitorScope(ConfigSelectorScope.LOCAL)
        .setMonitoringSystem(MonitoringSystem.SALUS)
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
}