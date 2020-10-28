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

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.event.discovery.EngineInstance;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.discovery.NoPartitionsAvailableException;
import com.rackspace.salus.event.manage.config.TestEventTaskProperties;
import com.rackspace.salus.event.manage.errors.BackendException;
import com.rackspace.salus.event.manage.model.GenericTaskCU;
import com.rackspace.salus.event.manage.model.TestTaskRequest;
import com.rackspace.salus.event.manage.model.TestTaskResult;
import com.rackspace.salus.event.manage.model.TestTaskResult.TestTaskResultData.EventResult;
import com.rackspace.salus.event.manage.services.KapacitorTaskIdGenerator.KapacitorTaskId;
import com.rackspace.salus.event.model.kapacitor.KapacitorEvent;
import com.rackspace.salus.event.model.kapacitor.KapacitorEvent.EventData;
import com.rackspace.salus.event.model.kapacitor.KapacitorEvent.SeriesItem;
import com.rackspace.salus.event.model.kapacitor.Task;
import com.rackspace.salus.event.model.kapacitor.Task.Stats;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Comparator;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.StateExpression;
import com.rackspace.salus.telemetry.model.SimpleNameTagValueMetric;
import com.rackspace.salus.test.JsonTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
        TestEventTaskService.class,
        KafkaTopicProperties.class,
        TestEventTaskProperties.class
    },
    properties = {
        // lower the kafka logging noise
        "logging.level.kafka=warn",
        "logging.level.org.apache=warn",
        "logging.level.kafka.server.LogDirFailureChannel=off",
        "logging.level.kafka.server.ReplicaManager=off",
        "logging.level.kafka.utils.CoreUtils$=off",
        // allows for either ordering of consumer/producer startup during test setup
        "spring.kafka.listener.missing-topics-fatal=false",
        // tell kafka listener the test-specific topic to use...gets bound into KafkaTopicProperties
        "salus.kafka.topics.testEventTaskResults=" + TestEventTaskServiceTest.RESULTS_TOPIC,
        "salus.event-engine-management.test-event-task.eventHandlerTopic="
            + TestEventTaskServiceTest.EVENT_HANDLER_TOPIC

    }
)
@EmbeddedKafka(
    partitions = 1, topics = {TestEventTaskServiceTest.RESULTS_TOPIC},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
// activate kakfa listener registration
@ImportAutoConfiguration({
    KafkaAutoConfiguration.class
})
// for mocking kapacitor endpoint as MockRestServiceServer
@AutoConfigureMockRestServiceServer
// autoconfig RestTemplateBuilder that is MockRestServiceServer aware
@AutoConfigureWebClient
// autoconfig a typical ObjectMapper
@AutoConfigureJson
@Import({SimpleMeterRegistry.class})
public class TestEventTaskServiceTest {

  public static final String RESULTS_TOPIC = "TestEventTaskServiceTest-results.json";
  public static final String EVENT_HANDLER_TOPIC = "TestEventTaskServiceTest-handler-topic";

  @Autowired
  MockRestServiceServer mockServer;

  @Autowired
  TestEventTaskService testEventTaskService;

  // Autowire the kafka broker registered via @EmbeddedKafka
  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private EmbeddedKafkaBroker embeddedKafka;

  @MockBean
  EventEnginePicker eventEnginePicker;

  @MockBean
  TickScriptBuilder tickScriptBuilder;

  @MockBean
  KapacitorTaskIdGenerator kapacitorTaskIdGenerator;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  TestEventTaskProperties testEventTaskProperties;

  @Rule
  public TestName testName = new TestName();

  @Before
  public void setUp() throws Exception {
    mockServer.reset();
  }

  @Test
  public void testPerformTestTask_single()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest(List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 90L))
    ));
    final String measurementName = ((GenericTaskCU) request.getTask()).getMeasurement();
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    final Task expectedTaskWithStats = setupMockKapacitorServer(expectedTaskCreate,
        taskId,
        true,
        false, List.of("cpu usage=90i")
    );

    when(tickScriptBuilder.build(anyString(), any(), any(), any(), anyBoolean()))
        .thenReturn("Mocked tick script content");

    // Initiate operation

    final CompletableFuture<TestTaskResult> completableResult = testEventTaskService
        .performTestTask("t-1", request);

    // Emulate Kapacitor event handler producing event to kafka
    produceTestResult(taskId, "CRITICAL", measurementName);

    // Assertions

    final TestTaskResult result = completableResult.get(5, TimeUnit.SECONDS);

    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getData().getStats()).isEqualTo(expectedTaskWithStats.getStats());

    assertThat(result.getData().getEvents()).hasSize(1);
    final EventResult actualEvent = result.getData().getEvents().get(0);
    assertThat(actualEvent).isNotNull();
    assertThat(actualEvent.getLevel()).isEqualTo("CRITICAL");
    assertThat(actualEvent.getData()).isNotNull();
    assertThat(actualEvent.getData().getSeries()).hasSize(1);
    assertThat(actualEvent.getData().getSeries().get(0).getName()).isEqualTo(measurementName);

    verifyEventEnginePicker(request);

    verifyTickScriptBuilder(request);

    mockServer.verify();
  }

  @Test
  public void testPerformTestTask_multiple()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest(List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 70L)),
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 90L))
    ));
    final String measurementName = ((GenericTaskCU) request.getTask()).getMeasurement();
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    final Task expectedTaskWithStats = setupMockKapacitorServer(expectedTaskCreate,
        taskId,
        true,
        false, List.of("cpu usage=70i", "cpu usage=90i")
    );

    when(tickScriptBuilder.build(anyString(), any(), any(), any(), anyBoolean()))
        .thenReturn("Mocked tick script content");

    // Initiate operation

    final CompletableFuture<TestTaskResult> completableResult = testEventTaskService
        .performTestTask("t-1", request);

    // give kafka consumer a moment to be ready
    Thread.sleep(500);
    // Emulate Kapacitor event handler producing event per metric to kafka
    produceTestResult(taskId, "OK", measurementName);
    produceTestResult(taskId, "CRITICAL", measurementName);

    // Assertions

    final TestTaskResult result = completableResult.get(5, TimeUnit.SECONDS);

    assertThat(result.getErrors()).isNullOrEmpty();
    assertThat(result.getData().getStats()).isEqualTo(expectedTaskWithStats.getStats());

    assertThat(result.getData().getEvents()).hasSize(2);
    assertThat(result.getData().getEvents().get(0)).isNotNull();
    assertThat(result.getData().getEvents().get(0).getLevel()).isEqualTo("OK");
    assertThat(result.getData().getEvents().get(0).getData()).isNotNull();
    assertThat(result.getData().getEvents().get(0).getData().getSeries()).hasSize(1);
    assertThat(result.getData().getEvents().get(0).getData().getSeries().get(0).getName())
        .isEqualTo(measurementName);
    assertThat(result.getData().getEvents().get(1)).isNotNull();
    assertThat(result.getData().getEvents().get(1).getLevel()).isEqualTo("CRITICAL");
    assertThat(result.getData().getEvents().get(1).getData()).isNotNull();
    assertThat(result.getData().getEvents().get(1).getData().getSeries()).hasSize(1);
    assertThat(result.getData().getEvents().get(1).getData().getSeries().get(0).getName())
        .isEqualTo(measurementName);

    verifyEventEnginePicker(request);

    verifyTickScriptBuilder(request);

    mockServer.verify();
  }

  @Test
  public void testPerformTestTask_partial()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest(List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 70L)),
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 90L))
    ));
    final String measurementName = ((GenericTaskCU) request.getTask()).getMeasurement();
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    final Task expectedTaskWithStats = setupMockKapacitorServer(expectedTaskCreate,
        taskId,
        true,
        false, List.of("cpu usage=70i", "cpu usage=90i")
    );

    when(tickScriptBuilder.build(anyString(), any(), any(), any(), anyBoolean()))
        .thenReturn("Mocked tick script content");

    // Initiate operation

    // shorten the completable future timeout since timeout is expected
    testEventTaskProperties.setEndToEndTimeout(Duration.ofSeconds(2));

    final CompletableFuture<TestTaskResult> completableResult = testEventTaskService
        .performTestTask("t-1", request);

    // give kafka consumer a moment to be ready
    Thread.sleep(500);
    // Only send one of the two expected results
    produceTestResult(taskId, "CRITICAL", measurementName);

    // Assertions

    final TestTaskResult result = completableResult.get(5, TimeUnit.SECONDS);

    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getData().getStats()).isEqualTo(expectedTaskWithStats.getStats());

    assertThat(result.getData().getEvents()).hasSize(1);
    assertThat(result.getData().getEvents().get(0)).isNotNull();
    assertThat(result.getData().getEvents().get(0).getLevel()).isEqualTo("CRITICAL");
    assertThat(result.getData().getEvents().get(0).getData()).isNotNull();
    assertThat(result.getData().getEvents().get(0).getData().getSeries()).hasSize(1);
    assertThat(result.getData().getEvents().get(0).getData().getSeries().get(0).getName())
        .isEqualTo(measurementName);

    verifyEventEnginePicker(request);

    verifyTickScriptBuilder(request);

    mockServer.verify();
  }

  @Test
  public void testPerformTestTask_noKapacitorResponse()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest(List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 90L))
    ));
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    setupMockKapacitorServer(expectedTaskCreate, taskId, true, false, List.of("cpu usage=90i"));

    when(tickScriptBuilder
        .build(anyString(), any(EventEngineTaskParameters.class), any(), any(), anyBoolean()))
        .thenReturn("Mocked tick script content");

    // Initiate operation

    testEventTaskProperties.setEndToEndTimeout(Duration.ofSeconds(2));

    final CompletableFuture<TestTaskResult> completableResult = testEventTaskService
        .performTestTask("t-1", request);

    // ...and do not send kapacitor event via kafka

    // Assertions

    final TestTaskResult result = completableResult.get(5, TimeUnit.SECONDS);
    assertThat(result.getErrors()).isNotEmpty();
    assertThat(result.getErrors().get(0).equals("Timed out waiting for test-event-task result"));

    verifyEventEnginePicker(request);

    verifyTickScriptBuilder(request);

    mockServer.verify();
  }

  @Test
  public void testPerformTestTask_failedMetricWrite()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest(List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 90L))
    ));
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    setupMockKapacitorServer(expectedTaskCreate, taskId, false, true, List.of("cpu usage=90i"));

    when(tickScriptBuilder.build(anyString(), any(), any(), any(), anyBoolean()))
        .thenReturn("Mocked tick script content");

    // Initiate operation

    final CompletableFuture<TestTaskResult> completableResult = testEventTaskService
        .performTestTask("t-1", request);

    // ...and do not send kapacitor event via kafka

    // Assertions

    assertThatThrownBy(() -> {
      completableResult.get(5, TimeUnit.SECONDS);
    })
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(BackendException.class)
        .hasMessageContaining("HTTP error while writing metric");

    verifyEventEnginePicker(request);

    verifyTickScriptBuilder(request);

    mockServer.verify();
  }

  @Test
  public void testPerformTestTask_noMetricName()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest(List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 90L))
    ));
    // test for manual validation of the metric data's name field
    request.getMetrics().get(0).setName(null);

    assertThatThrownBy(() -> {
      testEventTaskService
          .performTestTask("t-1", request);
    }).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Metric name is required on all metrics");
  }

  @Test
  public void testPerformTestTask_emptyValues()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest(List.of(
        new SimpleNameTagValueMetric()
            .setName("cpu")
            .setIvalues(Map.of("usage", 90L))
    ));
    // test for manual validation of the metric values
    request.getMetrics().get(0).setIvalues(Map.of());
    request.getMetrics().get(0).setFvalues(Map.of());
    request.getMetrics().get(0).setSvalues(Map.of());

    assertThatThrownBy(() -> {
      testEventTaskService
          .performTestTask("t-1", request);
    }).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("At least one metric value is required on all metrics");
  }

  private String setupKapacitorTaskId() {
    KapacitorTaskId kapacitorTaskId = new KapacitorTaskId()
        .setKapacitorTaskId(testName.getMethodName());
    when(kapacitorTaskIdGenerator.generateTaskId(any(), any()))
        .thenReturn(kapacitorTaskId);
    return kapacitorTaskId.getKapacitorTaskId();
  }

  private void verifyTickScriptBuilder(TestTaskRequest request) {
    verify(tickScriptBuilder).build(eq(((GenericTaskCU) request.getTask()).getMeasurement()),
        argThat(params -> {
          // spot check individual parts
          assertThat(params.getStateExpressions())
              .isEqualTo(request.getTask().getTaskParameters().getStateExpressions());
          return true;
        }),
        eq(testEventTaskProperties.getEventHandlerTopic()),
        eq(List.of(TickScriptBuilder.ID_PART_TASK_NAME)),
        eq(false)
    );
  }

  private Task setupMockKapacitorServer(String expectedTaskCreate, String taskId,
      boolean includeTaskGet, boolean failedMetricWrite,
      List<String> expectedMetricLines)
      throws JsonProcessingException {
    mockServer.expect(requestTo("http://localhost:0/kapacitor/v1/tasks"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(expectedTaskCreate))
        .andRespond(withSuccess());

    expectedMetricLines.forEach(line -> {
      mockServer.expect(
          requestToUriTemplate(
              "http://localhost:0/kapacitor/v1/write?db={taskId}&rp=ingest",
              taskId
          )
      )
          .andExpect(method(HttpMethod.POST))
          .andExpect(content().string(line + "\n"))
          .andRespond(
              failedMetricWrite ?
                  withStatus(HttpStatus.INTERNAL_SERVER_ERROR) :
                  withSuccess()
          );
    });

    Task expectedTaskWithStats = null;
    if (includeTaskGet) {
      expectedTaskWithStats = new Task()
          .setStats(
              new Stats()
                  .setNodeStats(
                      Map.of("alert2", Map.of("crits_triggered", 1))
                  )
          );
      mockServer.expect(
          requestToUriTemplate("http://localhost:0/kapacitor/v1/tasks/{taskId}", taskId)
      )
          .andExpect(method(HttpMethod.GET))
          .andRespond(
              withSuccess(
                  objectMapper.writeValueAsString(expectedTaskWithStats),
                  MediaType.APPLICATION_JSON)
          );
    }

    mockServer.expect(
        requestToUriTemplate("http://localhost:0/kapacitor/v1/tasks/{taskId}", taskId)
    )
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    return expectedTaskWithStats;
  }

  private void produceTestResult(String taskId, String level, String measurementName)
      throws JsonProcessingException {
    final EventData eventData = new EventData()
        .setSeries(List.of(
            new SeriesItem()
                .setName(measurementName)
        ));

    Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
    ProducerFactory<String, String> pf =
        new DefaultKafkaProducerFactory<>(producerProps);
    KafkaTemplate<String, String> template = new KafkaTemplate<>(pf);
    template.setDefaultTopic(RESULTS_TOPIC);
    final KapacitorEvent kapacitorEvent = new KapacitorEvent()
        .setId(taskId)
        .setLevel(level)
        .setData(eventData);
    template.sendDefault(objectMapper.writeValueAsString(kapacitorEvent));
  }

  private TestTaskRequest createTestTaskRequest(List<SimpleNameTagValueMetric> metrics) {
    return new TestTaskRequest()
        .setTask(
            new GenericTaskCU()
                .setMeasurement("cpu")
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
                                        .setValueName("usage")
                                        .setComparator(Comparator.GREATER_THAN)
                                        .setComparisonValue(80)
                                )
                            )
                        )
                )
        )
        .setMetrics(metrics);
  }

  private void setupEventEnginePicker() {
    // actual host:port is not important since RestTemplate call is mocked with given values
    final EngineInstance engineInstance = new EngineInstance("localhost", 0, 0);

    when(eventEnginePicker.pickAll())
        .thenReturn(List.of(engineInstance));
    try {
      when(eventEnginePicker.pickRecipient(any(), any(), any()))
          .thenReturn(engineInstance);
    } catch (NoPartitionsAvailableException e) {
      // this mock will never throw this
    }
  }

  private void verifyEventEnginePicker(TestTaskRequest request)
      throws NoPartitionsAvailableException {
    verify(eventEnginePicker).pickRecipient(
        "t-1", TestEventTaskService.PICKER_RESOURCE_ID, ((GenericTaskCU) request.getTask()).getMeasurement());
  }
}