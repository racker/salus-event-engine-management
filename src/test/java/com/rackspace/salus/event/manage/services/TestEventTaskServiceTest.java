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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.rackspace.salus.event.manage.errors.TestTimedOutException;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.model.TestTaskRequest;
import com.rackspace.salus.event.manage.model.TestTaskResult;
import com.rackspace.salus.event.manage.model.kapacitor.KapacitorEvent;
import com.rackspace.salus.event.manage.model.kapacitor.KapacitorEvent.EventData;
import com.rackspace.salus.event.manage.model.kapacitor.KapacitorEvent.SeriesItem;
import com.rackspace.salus.event.manage.model.kapacitor.Task;
import com.rackspace.salus.event.manage.model.kapacitor.Task.Stats;
import com.rackspace.salus.event.manage.services.KapacitorTaskIdGenerator.KapacitorTaskId;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Expression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LevelExpression;
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
        "logging.level.kafka=warn",
        "logging.level.org.apache=warn",
        // allows for either ordering of consumer/producer startup during test setup
        "spring.kafka.listener.missing-topics-fatal=false",
        // tell kafka listener the test-specific topic to use...gets bound into KafkaTopicProperties
        "salus.kafka.topics.testEventTaskResults=" + TestEventTaskServiceTest.RESULTS_TOPIC,
        "salus.event-engine-management.test-event-task.eventHandlerTopic=" + TestEventTaskServiceTest.EVENT_HANDLER_TOPIC

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

  // TODO test cases:
  // missing metric name
  // no values

  @Test
  public void testPerformTestTask_normal()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest();
    final String measurementName = request.getTask().getMeasurement();
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    final Task expectedTaskWithStats = setupMockKapacitorServer(expectedTaskCreate,
        taskId,
        true,
        false
    );

    when(tickScriptBuilder.build(any(), any(), any(), any(), any()))
        .thenReturn("Mocked tick script content");

    // Initiate operation

    final CompletableFuture<TestTaskResult> completableResult = testEventTaskService
        .performTestTask("t-1", request);

    // Emulate Kapacitor event handler producing event to kafka
    produceTestResult(taskId, "CRITICAL", measurementName);

    // Assertions

    final TestTaskResult result = completableResult.get(5, TimeUnit.SECONDS);

    assertThat(result.getStats()).isEqualTo(expectedTaskWithStats.getStats());

    assertThat(result.getEvent()).isNotNull();
    assertThat(result.getEvent().getLevel()).isEqualTo("CRITICAL");
    assertThat(result.getEvent().getData()).isNotNull();
    assertThat(result.getEvent().getData().getSeries()).hasSize(1);
    assertThat(result.getEvent().getData().getSeries().get(0).getName()).isEqualTo(measurementName);

    verifyEventEnginePicker(request);

    verifyTickScriptBuilder(request);

    mockServer.verify();
  }

  @Test
  public void testPerformTestTask_noKapacitorResponse()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest();
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    setupMockKapacitorServer(expectedTaskCreate, taskId, false, false);

    when(tickScriptBuilder.build(any(), any(), any(), any(), any()))
        .thenReturn("Mocked tick script content");

    // Initiate operation

    testEventTaskProperties.setEndToEndTimeout(Duration.ofSeconds(2));

    final CompletableFuture<TestTaskResult> completableResult = testEventTaskService
        .performTestTask("t-1", request);

    // ...and do not send kapacitor event via kafka

    // Assertions

    assertThatThrownBy(() -> {
      completableResult.get(5, TimeUnit.SECONDS);
    })
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(TestTimedOutException.class)
        .hasMessageContaining("Timed out waiting for test-event-task result");

    verifyEventEnginePicker(request);

    verifyTickScriptBuilder(request);

    mockServer.verify();
  }

  @Test
  public void testPerformTestTask_failedMetricWrite()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest();
    final String taskId = setupKapacitorTaskId();

    final String expectedTaskCreate = JsonTestUtils
        .readContent("/TestEventTaskServiceTest/task_create_request.json");

    setupMockKapacitorServer(expectedTaskCreate, taskId, false, true);

    when(tickScriptBuilder.build(any(), any(), any(), any(), any()))
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

    final TestTaskRequest request = createTestTaskRequest();
    // test for manual validation of the metric data's name field
    request.getMetric().setName(null);

    assertThatThrownBy(() -> {
      testEventTaskService
          .performTestTask("t-1", request);
    }).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Metric name is required");
  }

  @Test
  public void testPerformTestTask_emptyValues()
      throws InterruptedException, ExecutionException, TimeoutException, NoPartitionsAvailableException, IOException {
    setupEventEnginePicker();

    final TestTaskRequest request = createTestTaskRequest();
    // test for manual validation of the metric values
    request.getMetric().setIvalues(Map.of());
    request.getMetric().setFvalues(Map.of());
    request.getMetric().setSvalues(Map.of());

    assertThatThrownBy(() -> {
      testEventTaskService
          .performTestTask("t-1", request);
    }).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("At least one metric value is required");
  }

  private String setupKapacitorTaskId() {
    KapacitorTaskId kapacitorTaskId = new KapacitorTaskId()
        .setKapacitorTaskId(testName.getMethodName());
    when(kapacitorTaskIdGenerator.generateTaskId(any(), any()))
        .thenReturn(kapacitorTaskId);
    return kapacitorTaskId.getKapacitorTaskId();
  }

  private void verifyTickScriptBuilder(TestTaskRequest request) {
    verify(tickScriptBuilder).build(eq("t-1"), eq(request.getTask().getMeasurement()),
        argThat(params -> {
          // spot check individual parts
          assertThat(params.getCritical())
              .isEqualTo(request.getTask().getTaskParameters().getCritical());
          return true;
        }),
        eq(testEventTaskProperties.getEventHandlerTopic()),
        eq(List.of(TickScriptBuilder.ID_PART_TASK_NAME))
    );
  }

  private Task setupMockKapacitorServer(String expectedTaskCreate, String taskId,
                                        boolean includeTaskGet, boolean failedMetricWrite)
      throws JsonProcessingException {
    mockServer.expect(requestTo("http://localhost:0/kapacitor/v1/tasks"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(expectedTaskCreate))
        .andRespond(withSuccess());

    mockServer.expect(
        requestToUriTemplate(
            "http://localhost:0/kapacitor/v1/write?db={taskId}&rp=ingest",
            taskId
        )
    )
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string("cpu usage=90i\n"))
        .andRespond(
            failedMetricWrite ?
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR) :
                withSuccess()
        );

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
                  objectMapper.writeValueAsString(expectedTaskWithStats), MediaType.APPLICATION_JSON)
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

  private TestTaskRequest createTestTaskRequest() {
    return new TestTaskRequest()
        .setTask(
            new CreateTask()
                .setMeasurement("cpu")
                .setTaskParameters(
                    new EventEngineTaskParameters()
                        .setCritical(
                            new LevelExpression()
                                .setExpression(
                                    new Expression()
                                        .setField("usage")
                                        .setComparator(">")
                                        .setThreshold(80)
                                )
                        )
                )
        )
        .setMetric(
            new SimpleNameTagValueMetric()
                .setName("cpu")
                .setIvalues(Map.of("usage", 90L))
        );
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
        "t-1", TestEventTaskService.PICKER_RESOURCE_ID, request.getTask().getMeasurement());
  }
}