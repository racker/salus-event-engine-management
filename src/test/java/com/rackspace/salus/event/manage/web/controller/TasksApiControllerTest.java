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

package com.rackspace.salus.event.manage.web.controller;

import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static com.rackspace.salus.test.WebTestUtils.validationError;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.errors.TestTimedOutException;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.model.TestTaskRequest;
import com.rackspace.salus.event.manage.model.TestTaskResult;
import com.rackspace.salus.event.manage.model.TestTaskResult.TestTaskResultData;
import com.rackspace.salus.event.manage.model.TestTaskResult.TestTaskResultData.EventResult;
import com.rackspace.salus.event.manage.services.EventConversionService;
import com.rackspace.salus.event.manage.services.TasksService;
import com.rackspace.salus.event.manage.services.TestEventTaskService;
import com.rackspace.salus.event.model.kapacitor.KapacitorEvent.EventData;
import com.rackspace.salus.event.model.kapacitor.KapacitorEvent.SeriesItem;
import com.rackspace.salus.event.model.kapacitor.Task.Stats;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Comparator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LogicalExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LogicalExpression.Operator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.TaskState;
import com.rackspace.salus.telemetry.model.CustomEvalNode;
import com.rackspace.salus.telemetry.model.DerivativeNode;
import com.rackspace.salus.telemetry.model.MetricExpressionBase;
import com.rackspace.salus.telemetry.model.PercentageEvalNode;
import com.rackspace.salus.telemetry.model.SimpleNameTagValueMetric;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import com.rackspace.salus.telemetry.web.TenantVerification;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.co.jemos.podam.api.DefaultClassInfoStrategy;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = TasksApiController.class)
public class TasksApiControllerTest {

  // Ensure Expressions have their `threshold` field populated with something (a string).
  DefaultClassInfoStrategy classInfoStrategy;

  {
    try {
      classInfoStrategy = (DefaultClassInfoStrategy) DefaultClassInfoStrategy.getInstance()
          .addExtraMethod(ComparisonExpression.class, "podamHelper", String.class);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  PodamFactory podamFactory = new PodamFactoryImpl();

  @Autowired
  MockMvc mockMvc;

  @MockBean
  TasksService tasksService;

  @MockBean
  EventConversionService eventConversionService;

  @MockBean
  TestEventTaskService testEventTaskService;

  @MockBean
  EventEnginePicker eventEnginePicker;

  @MockBean
  TenantMetadataRepository tenantMetadataRepository;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  public void testTenantVerification_Success() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);

    doNothing().when(tasksService).deleteTask(anyString(), any());
    when(tenantMetadataRepository.existsByTenantId(tenantId))
        .thenReturn(true);

    mockMvc.perform(delete("/api/tenant/{tenantId}/tasks/{name}", tenantId, UUID.randomUUID())
        // header must be set to trigger tenant verification
        .header(TenantVerification.HEADER_TENANT, tenantId))
        .andExpect(status().isNoContent());

    verify(tenantMetadataRepository).existsByTenantId(tenantId);
  }

  @Test
  public void testTenantVerification_Fail() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);

    doNothing().when(tasksService).deleteTask(anyString(), any());
    when(tenantMetadataRepository.existsByTenantId(tenantId))
        .thenReturn(false);

    mockMvc.perform(delete("/api/tenant/{tenantId}/tasks/{name}", tenantId, UUID.randomUUID())
        // header must be set to trigger tenant verification
        .header(TenantVerification.HEADER_TENANT, tenantId))
        .andExpect(status().isNotFound())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message", is(TenantVerification.ERROR_MSG)));

    verify(tenantMetadataRepository).existsByTenantId(tenantId);
  }

  @Test
  public void testGetTasks() throws Exception {
    int numberOfTasks = 20;
    // Use the APIs default Pageable settings
    int page = 0;
    int pageSize = 20;
    List<EventEngineTask> tasks = new ArrayList<>();
    for (int i = 0; i < numberOfTasks; i++) {
      tasks.add(podamFactory.manufacturePojo(EventEngineTask.class));
    }

    int start = page * pageSize;
    int end = numberOfTasks;
    Page<EventEngineTask> pageOfTasks = new PageImpl<>(tasks.subList(start, end),
        PageRequest.of(page, pageSize),
        numberOfTasks);

    when(tasksService.getTasks(anyString(), any()))
        .thenReturn(pageOfTasks);

    String tenantId = RandomStringUtils.randomAlphabetic(8);

    mockMvc.perform(get("/api/tenant/{tenantId}/tasks", tenantId)
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content.*", hasSize(numberOfTasks)))
        .andExpect(jsonPath("$.totalPages", equalTo(1)))
        .andExpect(jsonPath("$.totalElements", equalTo(numberOfTasks)));

    verify(tasksService).getTasks(tenantId, PageRequest.of(0, 20));
    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testGetTask_testSerialization() throws Exception {
    String tenantId = "testSerialization";

    List<MetricExpressionBase> customMetrics = List.of(
        new PercentageEvalNode()
            .setPart("metric1")
            .setTotal("metric2")
            .setAs("xyPercent"),
        new DerivativeNode()
            .setMetric("testVal")
            .setDuration(Duration.ofSeconds(137))
            .setAs("new_rate"),
        new CustomEvalNode()
            .setOperator("-")
            .setOperands(List.of("field1", "field2"))
            .setAs("new_field"));

    EventEngineTask task = buildTask(tenantId, customMetrics);

    when(tasksService.getTask(anyString(), any()))
        .thenReturn(Optional.of(task));

    mockMvc.perform(get("/api/tenant/{tenantId}/tasks/{uuid}", tenantId, task.getId())
        .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().json(
            readContent("TasksControllerTest/get_task_response.json"), true));

    verify(tasksService).getTask(tenantId, task.getId());
    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testCreateTask() throws Exception {
    EventEngineTask task = podamFactory.manufacturePojo(EventEngineTask.class);
    when(tasksService.createTask(anyString(), any()))
        .thenReturn(task);

    String tenantId = RandomStringUtils.randomAlphabetic(8);

    CreateTask create = buildCreateTask(true);

    mockMvc.perform(post("/api/tenant/{tenantId}/tasks", tenantId)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    verify(tasksService).createTask(tenantId, create);
    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testUpdateTask() throws Exception {
    EventEngineTask task = podamFactory.manufacturePojo(EventEngineTask.class);
    when(tasksService.updateTask(any()))
        .thenReturn(task);

    String tenantId = RandomStringUtils.randomAlphabetic(8);
    UUID uuid = UUID.randomUUID();

    CreateTask create = buildCreateTask(true);
    EventEngineTask eventEngineTask = eventConversionService.convertFromInput(tenantId, uuid, create);

    mockMvc.perform(put("/api/tenant/{tenantId}/tasks/{uuid}", tenantId, uuid)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andDo(print())
        .andExpect(status().is2xxSuccessful())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    verify(tasksService).updateTask(eventEngineTask);
    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testCreateTask_invalidBasicEvalNode() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);

    List<MetricExpressionBase> customMetrics = List.of(
        new CustomEvalNode()
            .setOperands(List.of("metric1", "metric2", "invalidFunction(test, blah)"))
            .setOperator("+")
            .setAs("new_metric"));

    CreateTask create = buildCreateTask(true, customMetrics);

    mockMvc.perform(post("/api/tenant/{tenantId}/tasks", tenantId)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(validationError("taskParameters.customMetrics[0]",
            "Invalid custom metric."));
  }

  @Test
  public void testCreateTask_tooManyDerivativeNodes() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic(8);

    List<MetricExpressionBase> customMetrics = List.of(
        new DerivativeNode()
            .setMetric("metric1")
            .setAs("rate1"),
        new DerivativeNode()
            .setMetric("metric2")
            .setAs("rate2"));

    CreateTask create = buildCreateTask(true, customMetrics);

    mockMvc.perform(post("/api/tenant/{tenantId}/tasks", tenantId)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(status().isBadRequest())
        .andExpect(validationError("taskParameters.customMetrics",
            "Using multiple 'rate' metrics is not supported."));
  }

  @Test
  public void testCreateTask_MissingName() throws Exception {
    EventEngineTask task = podamFactory.manufacturePojo(EventEngineTask.class);
    when(tasksService.createTask(anyString(), any()))
        .thenReturn(task);

    String tenantId = RandomStringUtils.randomAlphabetic(8);

    CreateTask create = buildCreateTask(false);

    mockMvc.perform(post("/api/tenant/{tenantId}/tasks", tenantId)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(validationError("name", "must not be empty"));

    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testDeleteTask() throws Exception {
    doNothing().when(tasksService).deleteTask(anyString(), any());

    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    mockMvc.perform(delete(
        "/api/tenant/{tenantId}/tasks/{name}",
        "t-1", id))
        .andDo(print())
        .andExpect(status().isNoContent());

    verify(tasksService).deleteTask("t-1", id);
    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testTestEventTask_normal() throws Exception {
    final String tenantId = RandomStringUtils.randomAlphabetic(8);

    final CreateTask createTask = buildCreateTask(true);
    // ...but ensure user doesn't have to name tasks being tested
    createTask.setName(null);
    final TestTaskRequest testTaskRequest = new TestTaskRequest()
        .setTask(createTask)
        .setMetrics(List.of(
            podamFactory.manufacturePojo(SimpleNameTagValueMetric.class)
                .setName(createTask.getMeasurement())
        ));

    final TestTaskResult expectedResult = new TestTaskResult()
        .setData(new TestTaskResultData().setEvents(List.of(
            new EventResult()
                .setData(
                    new EventData()
                        .setSeries(List.of(
                            new SeriesItem()
                                .setName(testTaskRequest.getTask().getMeasurement())
                        ))
                )
                .setLevel("CRITICAL"))).setStats(
            new Stats()
                .setNodeStats(Map.of("alert2", Map.of("crits_triggered", 1)))
        ));

    when(testEventTaskService.performTestTask(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(expectedResult));

    final MvcResult mvcResult = mockMvc.perform(post("/api/tenant/{tenantId}/test-task", tenantId)
        .content(objectMapper.writeValueAsString(testTaskRequest))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors",
            equalTo(null)))
        .andExpect(jsonPath("$.data.events[0].level",
            equalTo("CRITICAL")))
        .andExpect(jsonPath("$.data.events[0].data.series[0].name",
            equalTo(testTaskRequest.getTask().getMeasurement())))
        .andExpect(jsonPath("$.data.stats.node-stats.alert2.crits_triggered",
            equalTo(1)))
    ;

    verify(testEventTaskService).performTestTask(tenantId, testTaskRequest);

    verifyNoMoreInteractions(tasksService, testEventTaskService);
  }

  @Test
  public void testTestEventTask_partial() throws Exception {
    final String tenantId = RandomStringUtils.randomAlphabetic(8);

    final CreateTask createTask = buildCreateTask(true);
    // ...but ensure user doesn't have to name tasks being tested
    createTask.setName(null);
    final TestTaskRequest testTaskRequest = new TestTaskRequest()
        .setTask(createTask)
        .setMetrics(List.of(
            // send in two metrics
            podamFactory.manufacturePojo(SimpleNameTagValueMetric.class)
                .setName(createTask.getMeasurement()),
            podamFactory.manufacturePojo(SimpleNameTagValueMetric.class)
                .setName(createTask.getMeasurement())
        ));

    final TestTaskResult expectedResult = new TestTaskResult()
        // but only get a partial result
        .setErrors(List.of("Timed out waiting for test-event-task result"))
        // ...of one event
        .setData(new TestTaskResultData()
            .setEvents(List.of(
                new EventResult()
                    .setData(
                        new EventData()
                            .setSeries(List.of(
                                new SeriesItem()
                                    .setName(testTaskRequest.getTask().getMeasurement())
                            ))
                    )
                    .setLevel("CRITICAL")
            ))
            .setStats(
                new Stats()
                    .setNodeStats(Map.of("alert2", Map.of("crits_triggered", 1)))
            ));

    when(testEventTaskService.performTestTask(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(expectedResult));

    final MvcResult mvcResult = mockMvc.perform(post("/api/tenant/{tenantId}/test-task", tenantId)
        .content(objectMapper.writeValueAsString(testTaskRequest))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors[0]",
            equalTo("Timed out waiting for test-event-task result")))
        .andExpect(jsonPath("$.data.events[0].level",
            equalTo("CRITICAL")))
        .andExpect(jsonPath("$.data.events[0].data.series[0].name",
            equalTo(testTaskRequest.getTask().getMeasurement())))
        .andExpect(jsonPath("$.data.stats.node-stats.alert2.crits_triggered",
            equalTo(1)))
    ;

    verify(testEventTaskService).performTestTask(tenantId, testTaskRequest);

    verifyNoMoreInteractions(tasksService, testEventTaskService);
  }

  @Test
  public void testTestEventTask_timedOut() throws Exception {
    final String tenantId = RandomStringUtils.randomAlphabetic(8);

    final CreateTask createTask = buildCreateTask(true);
    // ...but ensure user doesn't have to name tasks being tested
    createTask.setName(null);
    final TestTaskRequest testTaskRequest = new TestTaskRequest()
        .setTask(createTask)
        .setMetrics(List.of(
            podamFactory.manufacturePojo(SimpleNameTagValueMetric.class)
                .setName(createTask.getMeasurement())
        ));

    final CompletableFuture<TestTaskResult> completableFuture = new CompletableFuture<>();
    completableFuture.completeExceptionally(new TestTimedOutException("time out", null));

    when(testEventTaskService.performTestTask(any(), any()))
        .thenReturn(completableFuture);

    final MvcResult mvcResult = mockMvc.perform(post("/api/tenant/{tenantId}/test-task", tenantId)
        .content(objectMapper.writeValueAsString(testTaskRequest))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(mvcResult))
        .andExpect(status().isGatewayTimeout())
    ;

    verify(testEventTaskService).performTestTask(tenantId, testTaskRequest);

    verifyNoMoreInteractions(tasksService, testEventTaskService);
  }

  private static CreateTask buildCreateTask(boolean setName) {
    return buildCreateTask(setName, null);
  }

  private static CreateTask buildCreateTask(boolean setName,
      List<MetricExpressionBase> customMetrics) {
    return new CreateTask()
        .setName(setName ? "this is my name" : null)
        .setMeasurement("cpu")
        .setTaskParameters(
            new EventEngineTaskParameters()
                .setLabelSelector(
                    singletonMap("agent_environment", "localdev")
                )
                .setMessageTemplate("The CPU usage was too high")
                .setCustomMetrics(customMetrics)
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

  private static EventEngineTask buildTask(String tenantId) {
    return buildTask(tenantId, null);

  }

  private static EventEngineTask buildTask(String tenantId,
      List<MetricExpressionBase> customMetrics) {
    return new EventEngineTask()
        .setId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
        .setKapacitorTaskId("testTaskId")
        .setCreatedTimestamp(Instant.EPOCH)
        .setUpdatedTimestamp(Instant.EPOCH)
        .setTenantId(tenantId)
        .setName("my-test-task")
        .setMeasurement("disk")
        .setTaskParameters(
            new EventEngineTaskParameters()
                .setLabelSelector(
                    singletonMap("discovered_os", "linux")
                )
                .setCriticalStateDuration(5)
                .setStateExpressions(List.of(
                    new StateExpression()
                        .setState(TaskState.CRITICAL)
                        .setMessage("critical threshold was hit")
                        .setExpression(
                            new LogicalExpression()
                                .setOperator(Operator.OR)
                                .setExpressions(List.of(
                                    new ComparisonExpression()
                                        .setValueName("usage_user")
                                        .setComparator(Comparator.GREATER_THAN)
                                        .setComparisonValue(75),
                                    new ComparisonExpression()
                                        .setValueName("usage_system")
                                        .setComparator(Comparator.EQUAL_TO)
                                        .setComparisonValue(92)))
                        )
                ))
                .setCustomMetrics(customMetrics)
        );
  }
}
