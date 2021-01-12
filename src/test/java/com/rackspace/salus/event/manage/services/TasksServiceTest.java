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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.rackspace.salus.event.manage.config.DatabaseConfig;
import com.rackspace.salus.event.manage.model.TaskCU;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Comparator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.messaging.TaskChangeEvent;
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
    DatabaseConfig.class
})
@AutoConfigureDataJpa
@EnableTestContainersDatabase
@Import({SimpleMeterRegistry.class})
public class TasksServiceTest {

  @Autowired
  TasksService tasksService;

  @MockBean
  TaskEventProducer taskEventProducer;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // false report by IntelliJ
  @Autowired
  EventEngineTaskRepository eventEngineTaskRepository;

  @After
  public void tearDown() {
    eventEngineTaskRepository.deleteAll();
  }

  @Test
  public void testCreate_success() {
    final TaskCU taskIn = buildCreateTask();

    final EventEngineTask created = tasksService.createTask("t-1", taskIn);

    assertThat(created).isNotNull();

    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isPresent();

    // timestamps are returned with slightly less precision on the retrieval
    assertThat(retrieved.get()).isEqualToIgnoringGivenFields(created, "createdTimestamp", "updatedTimestamp");
    assertThat(retrieved.get().getMonitoringSystem()).isEqualTo(MonitoringSystem.SALUS);
    assertThat(retrieved.get().getPartition()).isEqualTo(6);

    verify(taskEventProducer).sendTaskChangeEvent(
        new TaskChangeEvent()
            .setTenantId(created.getTenantId())
            .setTaskId(created.getId())
    );

    verifyNoMoreInteractions(taskEventProducer);
  }

  @Test
  public void testDeleteTask_success() {
    EventEngineTask created = saveTask();
    Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isPresent();

    tasksService.deleteTask(created.getTenantId(), created.getId());

    retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isEmpty();

    verify(taskEventProducer).sendTaskChangeEvent(
        new TaskChangeEvent()
            .setTenantId(created.getTenantId())
            .setTaskId(created.getId())
    );

    verifyNoMoreInteractions(taskEventProducer);
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

    verifyNoMoreInteractions(taskEventProducer);
  }

  @Test
  public void testDeleteTask_tenantMismatch() {
    String wrongTenantId = RandomStringUtils.randomAlphabetic(5);

    EventEngineTask created = saveTask();
    Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(created.getId());
    assertThat(retrieved).isPresent();

    assertThatThrownBy(() -> {
      tasksService.deleteTask(wrongTenantId, created.getId());
    })
        .isInstanceOf(NotFoundException.class)
        .hasMessage(String.format("No task found for %s on tenant %s",
            created.getId(), wrongTenantId));

    verifyNoMoreInteractions(taskEventProducer);
  }

  @Test
  public void testDeleteAllTasksForTenant() {
    EventEngineTask task1 = saveTask();
    EventEngineTask task2 = saveTask();

    Page<EventEngineTask> retrieved = eventEngineTaskRepository.findByTenantId(
        "t-1", Pageable.unpaged());
    assertThat(retrieved).hasSize(2);

    tasksService.deleteAllTasksForTenant("t-1");

    retrieved = eventEngineTaskRepository.findByTenantId("t-1", Pageable.unpaged());
    assertThat(retrieved).isEmpty();

    verify(taskEventProducer).sendTaskChangeEvent(
        new TaskChangeEvent()
            .setTenantId(task1.getTenantId())
            .setTaskId(task1.getId())
    );
    verify(taskEventProducer).sendTaskChangeEvent(
        new TaskChangeEvent()
            .setTenantId(task2.getTenantId())
            .setTaskId(task2.getId())
    );

    verifyNoMoreInteractions(taskEventProducer);
  }

  @Test
  @Transactional
  public void testUpdate_update_name() {
    EventEngineTask saved = saveTask();
    assertThat(saved.getPartition()).isEqualTo(0);

    TaskCU taskCU = new TaskCU().setName("new task name");
    final EventEngineTask updated = tasksService.updateTask(saved.getTenantId(), saved.getId(), taskCU);

    assertThat(updated).isNotNull();
    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(updated.getId());
    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().getName()).isEqualTo("new task name");
    assertThat(retrieved.get().getPartition()).isEqualTo(0); // partition is unchanged

    verifyNoMoreInteractions(taskEventProducer);
  }

  /**
   * Store an event task in sql.
   * Reuse values from buildSalusCreateTask to keep objects consistent.
   *
   * @return The saved task.
   */
  private EventEngineTask saveTask() {
    final TaskCU taskIn = buildCreateTask();

    final EventEngineTask eventEngineTask = new EventEngineTask()
        .setId(UUID.randomUUID())
        .setName(taskIn.getName())
        .setTenantId("t-1")
        .setTaskParameters(taskIn.getTaskParameters())
        .setMonitoringSystem(taskIn.getMonitoringSystem())
        .setPartition(0);
    return eventEngineTaskRepository.save(eventEngineTask);
  }

  private static TaskCU buildCreateTask() {
    return new TaskCU()
        .setMonitoringSystem(MonitoringSystem.SALUS)
        .setName("task-1")
        .setTaskParameters(
            new EventEngineTaskParameters()
                .setMetricGroup("mem")
                .setLabelSelector(
                    singletonMap("agent_environment", "localdev")
                )
                .setStateExpressions(List.of(
                    new StateExpression()
                        .setExpression(
                            new ComparisonExpression()
                                .setInput("usage_user")
                                .setComparator(Comparator.GREATER_THAN)
                                .setComparisonValue(75)
                        )
                    )
                ));
  }
}