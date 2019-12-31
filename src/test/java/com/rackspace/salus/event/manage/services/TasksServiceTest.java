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
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.rackspace.salus.event.discovery.EngineInstance;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.config.DatabaseConfig;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.services.KapacitorTaskIdGenerator.KapacitorTaskId;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Expression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LevelExpression;
import com.rackspace.salus.telemetry.repositories.EventEngineTaskRepository;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.ResourceAccessException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    TasksService.class,
    DatabaseConfig.class
})
@AutoConfigureDataJpa
@AutoConfigureTestDatabase
// for mocking kapacitor interactions
@AutoConfigureWebClient
@AutoConfigureMockRestServiceServer
public class TasksServiceTest {

  @Autowired
  TasksService tasksService;

  @Autowired
  private MockRestServiceServer mockKapacitorServer;

  @MockBean
  EventEnginePicker eventEnginePicker;

  @Autowired
  EventEngineTaskRepository eventEngineTaskRepository;

  @MockBean
  KapacitorTaskIdGenerator kapacitorTaskIdGenerator;

  @MockBean
  TickScriptBuilder tickScriptBuilder;

  @MockBean
  AccountQualifierService accountQualifierService;

  @After
  public void tearDown() throws Exception {
    eventEngineTaskRepository.deleteAll();
  }

  @Test
  public void testCreate_success() throws IOException {

    final KapacitorTaskId taskId = new KapacitorTaskId()
        .setBaseId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        .setKapacitorTaskId("k-1");
    when(kapacitorTaskIdGenerator.generateTaskId(any(), any()))
        .thenReturn(taskId
        );

    when(tickScriptBuilder.build(any(), any(), any()))
        .thenReturn("built script");

    when(accountQualifierService.convertFromTenant(any()))
        .then(invocationOnMock -> "TYPE:" + invocationOnMock.getArgument(0));

    when(eventEnginePicker.pickAll())
        .thenReturn(Arrays.asList(
            new EngineInstance("host", 1000, 0),
            new EngineInstance("host", 1001, 1)
        ));

    final String requestJson = readContent("/TasksServiceTest/request.json");

    final String responseJson = readContent("/TasksServiceTest/response_success.json");

    mockKapacitorServer
        .expect(requestTo("http://host:1000/kapacitor/v1/tasks"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(requestJson))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
    mockKapacitorServer
        .expect(requestTo("http://host:1001/kapacitor/v1/tasks"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(requestJson))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

    final CreateTask taskIn = buildCreateTask();

    // EXECUTE

    final EventEngineTask result = tasksService.createTask("t-1", taskIn);

    // VERIFY

    assertThat(result).isNotNull();

    verify(kapacitorTaskIdGenerator).generateTaskId("t-1", "cpu");

    verify(tickScriptBuilder).build("t-1", "cpu", taskIn.getTaskParameters());

    verify(accountQualifierService).convertFromTenant("t-1");

    verify(eventEnginePicker).pickAll();

    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(result.getId());
    assertThat(retrieved).isPresent();

    mockKapacitorServer.verify();

    verifyNoMoreInteractions(eventEnginePicker, kapacitorTaskIdGenerator,
        tickScriptBuilder
    );
  }

  @Test
  public void testCreate_fail_connectionRefused() throws IOException {

    common_testCreate_fail(this::connectionRefusedCreator);
  }

  @Test
  public void testCreate_fail_badRequest() throws IOException {
    common_testCreate_fail(withBadRequest());
  }

  @Test
  public void testCreate_fail_emptyRespBody() throws IOException {
    common_testCreate_fail(withSuccess("", MediaType.APPLICATION_JSON));
  }

  @Test
  public void testCreate_fail_errorInResp() throws IOException {
    final String errorResponseJson = readContent("/TasksServiceTest/response_error.json");

    common_testCreate_fail(withSuccess(errorResponseJson, MediaType.APPLICATION_JSON));
  }

  private void common_testCreate_fail(ResponseCreator responseCreator) throws IOException {

    final KapacitorTaskId taskId = new KapacitorTaskId()
        .setBaseId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        .setKapacitorTaskId("k-1");
    when(kapacitorTaskIdGenerator.generateTaskId(any(), any()))
        .thenReturn(taskId
        );

    when(tickScriptBuilder.build(any(), any(), any()))
        .thenReturn("built script");

    when(accountQualifierService.convertFromTenant(any()))
        .then(invocationOnMock -> "TYPE:" + invocationOnMock.getArgument(0));

    when(eventEnginePicker.pickAll())
        .thenReturn(Arrays.asList(
            new EngineInstance("host", 1000, 0),
            new EngineInstance("host", 1001, 1)
        ));

    final String requestJson = readContent("/TasksServiceTest/request.json");

    final String responseJson = readContent("/TasksServiceTest/response_success.json");

    // simulate instance #0 is fine
    mockKapacitorServer
        .expect(requestTo("http://host:1000/kapacitor/v1/tasks"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(requestJson))
        .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
    mockKapacitorServer
        .expect(requestTo("http://host:1001/kapacitor/v1/tasks"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(requestJson))
        // simulate offline
        .andRespond(responseCreator);

    // expect and verify the rollback deletion from instance #0
    mockKapacitorServer
        .expect(requestTo("http://host:1000/kapacitor/v1/tasks/k-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withNoContent());

    final CreateTask taskIn = buildCreateTask();

    // EXECUTE

    assertThatThrownBy(() -> {
      tasksService.createTask("t-1", taskIn);
    }).isInstanceOf(BackendException.class);

    // VERIFY

    verify(kapacitorTaskIdGenerator).generateTaskId("t-1", "cpu");

    verify(tickScriptBuilder).build("t-1", "cpu", taskIn.getTaskParameters());

    verify(accountQualifierService).convertFromTenant("t-1");

    verify(eventEnginePicker).pickAll();

    mockKapacitorServer.verify();

    final Iterable<EventEngineTask> retrieved = eventEngineTaskRepository.findAll();
    assertThat(retrieved).isEmpty();

    verifyNoMoreInteractions(eventEnginePicker, kapacitorTaskIdGenerator,
        tickScriptBuilder
    );
  }

  @Test
  public void testCreate_fail_noEngineInstances() throws IOException {
    final KapacitorTaskId taskId = new KapacitorTaskId()
        .setBaseId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        .setKapacitorTaskId("k-1");
    when(kapacitorTaskIdGenerator.generateTaskId(any(), any()))
        .thenReturn(taskId
        );

    when(eventEnginePicker.pickAll())
        .thenReturn(Collections.emptyList());

    final CreateTask taskIn = buildCreateTask();

    // EXECUTE

    assertThatThrownBy(() -> {
      tasksService.createTask("t-1", taskIn);
    }).isInstanceOf(IllegalStateException.class);

    // VERIFY

    // DB save should have been rolled back by failed transaction
    final Iterable<EventEngineTask> tasks = eventEngineTaskRepository.findAll();
    assertThat(tasks).isEmpty();

    verify(kapacitorTaskIdGenerator).generateTaskId("t-1", "cpu");

    verify(tickScriptBuilder).build("t-1", "cpu", taskIn.getTaskParameters());

    verify(eventEnginePicker).pickAll();

    verifyNoMoreInteractions(eventEnginePicker, kapacitorTaskIdGenerator,
        tickScriptBuilder
    );
  }

  @Test
  public void testDeleteTask_success() {
    final UUID taskDbId = UUID.randomUUID();

    saveTask(taskDbId);

    when(eventEnginePicker.pickAll())
        .thenReturn(Arrays.asList(
            new EngineInstance("host", 1000, 0),
            new EngineInstance("host", 1001, 1)
        ));

    mockKapacitorServer
        .expect(requestTo("http://host:1000/kapacitor/v1/tasks/k-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withNoContent());
    mockKapacitorServer
        .expect(requestTo("http://host:1001/kapacitor/v1/tasks/k-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withNoContent());

    // EXECUTE

    tasksService.deleteTask("t-1", taskDbId);

    // VERIFY

    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(taskDbId);
    assertThat(retrieved).isEmpty();

    verify(eventEnginePicker).pickAll();

    mockKapacitorServer.verify();

    verifyNoMoreInteractions(eventEnginePicker, kapacitorTaskIdGenerator,
        tickScriptBuilder
    );
  }

  @Test
  public void testDeleteTask_missingTask() {
    final UUID taskDbId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // EXECUTE

    assertThatThrownBy(() -> {
      tasksService.deleteTask("t-1", taskDbId);
    }).isInstanceOf(NotFoundException.class);

    // VERIFY

    mockKapacitorServer.verify();

    verifyNoMoreInteractions(eventEnginePicker, kapacitorTaskIdGenerator,
        tickScriptBuilder
    );
  }

  @Test
  public void testDeleteTask_noEngineInstances() {
    final UUID taskDbId = UUID.randomUUID();

    saveTask(taskDbId);

    when(eventEnginePicker.pickAll())
        .thenReturn(Collections.emptyList());

    // EXECUTE

    assertThatThrownBy(() -> {
      tasksService.deleteTask("t-1", taskDbId);
    }).isInstanceOf(IllegalStateException.class);

    // VERIFY

    verify(eventEnginePicker).pickAll();

    // DB deletion should have been rolled back with failed trasnaction
    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(taskDbId);
    assertThat(retrieved).isPresent();

    verifyNoMoreInteractions(eventEnginePicker, kapacitorTaskIdGenerator,
        tickScriptBuilder
    );
  }

  @Test
  public void testDeleteTask_tenantMismatch() {
    final UUID taskDbId = UUID.randomUUID();

    saveTask(taskDbId);

    // EXECUTE

    assertThatThrownBy(() -> {
      tasksService.deleteTask("t-someone-else", taskDbId);
    }).isInstanceOf(NotFoundException.class);

    // VERIFY

    final Optional<EventEngineTask> retrieved = eventEngineTaskRepository.findById(taskDbId);
    assertThat(retrieved).isPresent();

    mockKapacitorServer.verify();

    verifyNoMoreInteractions(eventEnginePicker, kapacitorTaskIdGenerator,
        tickScriptBuilder
    );
  }

  private void saveTask(UUID taskDbId) {
    final EventEngineTask eventEngineTask = new EventEngineTask()
        .setId(taskDbId)
        .setName("task-1")
        .setTenantId("t-1")
        .setKapacitorTaskId("k-1")
        .setMeasurement("cpu")
        .setTaskParameters(new EventEngineTaskParameters());
    eventEngineTaskRepository.save(eventEngineTask);
  }

  private ClientHttpResponse connectionRefusedCreator(ClientHttpRequest clientHttpRequest) {
    throw new ResourceAccessException(
        "I/O error on POST request for \"http://localhost:9193/kapacitor/v1/tasks\"",
        new ConnectException("Connection refused")
    );
  }

  private static CreateTask buildCreateTask() {
    return new CreateTask()
        .setName("task-1")
        .setMeasurement("cpu")
        .setTaskParameters(
            new EventEngineTaskParameters()
                .setLabelSelector(
                    singletonMap("agent_environment", "localdev")
                )
                .setCritical(
                    new LevelExpression()
                        .setConsecutiveCount(1)
                        .setExpression(
                            new Expression()
                                .setField("usage_user")
                                .setComparator(">")
                                .setThreshold(75)
                        )
                )
        );
  }
}