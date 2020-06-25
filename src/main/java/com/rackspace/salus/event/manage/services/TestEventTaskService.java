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

import com.rackspace.salus.common.messaging.KafkaTopicProperties;
import com.rackspace.salus.event.common.InfluxScope;
import com.rackspace.salus.event.discovery.EngineInstance;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.discovery.NoPartitionsAvailableException;
import com.rackspace.salus.event.manage.config.TestEventTaskProperties;
import com.rackspace.salus.event.manage.errors.BackendException;
import com.rackspace.salus.event.manage.errors.TestTimedOutException;
import com.rackspace.salus.event.manage.model.TestTaskRequest;
import com.rackspace.salus.event.manage.model.TestTaskResult;
import com.rackspace.salus.event.manage.model.TestTaskResult.EventResult;
import com.rackspace.salus.event.manage.model.kapacitor.DbRp;
import com.rackspace.salus.event.manage.model.kapacitor.KapacitorEvent;
import com.rackspace.salus.event.manage.model.kapacitor.Task;
import com.rackspace.salus.event.manage.model.kapacitor.Task.Status;
import com.rackspace.salus.event.manage.model.kapacitor.Task.Type;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LevelExpression;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.influxdb.dto.Point;
import org.influxdb.dto.Point.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class TestEventTaskService {

  static final String PICKER_RESOURCE_ID = "test-event-task";
  static final String TASK_ID_PREFIX = "test-task-";

  private final EventEnginePicker eventEnginePicker;
  private final KapacitorTaskIdGenerator taskIdGenerator;
  private final TickScriptBuilder tickScriptBuilder;
  private final KafkaTopicProperties kafkaTopicProperties;
  private final TestEventTaskProperties testEventTaskProperties;
  private final String appName;
  private final String ourHostName;
  private final RestTemplate restTemplate;

  private final ConcurrentHashMap<String/*correlationId*/, CompletableFuture<TestTaskResult>> pending =
      new ConcurrentHashMap<>();
  private final Timer timerDuration;
  private final Counter counterTimedOut;
  private final Counter counterSuccess;
  private final Counter counterFinishWithException;
  private final Counter counterSetupFailed;
  private final Counter counterFailedToDelete;
  private final Counter counterConsumerIgnored;
  private final Counter counterConsumerConsumed;
  private final Counter counterConsumerMissingId;

  @Autowired
  public TestEventTaskService(EventEnginePicker eventEnginePicker,
                              RestTemplateBuilder restTemplateBuilder,
                              MeterRegistry meterRegistry,
                              KapacitorTaskIdGenerator taskIdGenerator,
                              TickScriptBuilder tickScriptBuilder,
                              KafkaTopicProperties kafkaTopicProperties,
                              TestEventTaskProperties testEventTaskProperties,
                              @Value("${spring.application.name}") String appName,
                              @Value("${localhost.name}") String ourHostName) {
    this.eventEnginePicker = eventEnginePicker;
    this.restTemplate = restTemplateBuilder.build();
    this.taskIdGenerator = taskIdGenerator;
    this.tickScriptBuilder = tickScriptBuilder;
    this.kafkaTopicProperties = kafkaTopicProperties;
    this.testEventTaskProperties = testEventTaskProperties;
    this.appName = appName;
    this.ourHostName = ourHostName;

    meterRegistry.gaugeMapSize("test-event-tasks.pending", List.of(), pending);
    timerDuration = meterRegistry.timer("test-event-tasks.duration");
    counterTimedOut = meterRegistry.counter("test-event-tasks.timed-out");
    counterFinishWithException = meterRegistry.counter("test-event-tasks.exception");
    counterSuccess = meterRegistry.counter("test-event-tasks.success");
    counterSetupFailed = meterRegistry.counter("test-event-tasks.setup-failed");
    counterFailedToDelete = meterRegistry.counter("test-event-tasks.failed-to-delete");

    counterConsumerConsumed = meterRegistry.counter("test-event-tasks.consumer.consumed");
    counterConsumerMissingId = meterRegistry.counter("test-event-tasks.consumer.missing-id");
    counterConsumerIgnored = meterRegistry.counter("test-event-tasks.consumer.ignored");
  }

  public CompletableFuture<TestTaskResult> performTestTask(String tenantId,
                                                           TestTaskRequest request) {
    Assert.hasText(request.getMetric().getName(), "Metric name is required");
    Assert.isTrue(

        hasValues(request.getMetric().getFvalues()) ||
            hasValues(request.getMetric().getIvalues()) ||
            hasValues(request.getMetric().getSvalues()),
        "At least one metric value is required"
    );

    final EngineInstance engineInstance;
    try {
      engineInstance = eventEnginePicker
          .pickRecipient(tenantId, PICKER_RESOURCE_ID, request.getMetric().getName());
    } catch (NoPartitionsAvailableException e) {
      throw new IllegalStateException("Unable to locate kapacitor instance", e);
    }

    final String taskId =
        taskIdGenerator.generateTaskId(
            tenantId, TASK_ID_PREFIX +request.getMetric().getName()
        ).getKapacitorTaskId();

    createTask(tenantId, request, engineInstance, taskId);

    final CompletableFuture<TestTaskResult> result = new CompletableFuture<TestTaskResult>()
        .orTimeout(testEventTaskProperties.getEndToEndTimeout().getSeconds(), TimeUnit.SECONDS);

    pending.put(taskId, result);

    final CompletableFuture<TestTaskResult> interceptedResult = result.handle(
        handleTestTaskCompletion(engineInstance, taskId)
    );

    try {
      postMetric(request, engineInstance, taskId);
    } catch (Exception e) {
      result.completeExceptionally(e);
    }

    return interceptedResult;
  }

  private static boolean hasValues(Map<String, ?> values) {
    return values != null && !values.isEmpty();
  }

  private BiFunction<TestTaskResult, Throwable, TestTaskResult> handleTestTaskCompletion(
      EngineInstance engineInstance, String taskId) {
    final Instant startTime = Instant.now();

    return (testTaskResult, throwable) -> {
      timerDuration.record(Duration.between(startTime, Instant.now()));
      pending.remove(taskId);
      if (throwable == null) {
        // completed successfully, so augment with stats tracked by kapacitor

        log.debug("Got test-task result={} id={}", testTaskResult, taskId);
        try {
          testTaskResult.setStats(
              getTaskStats(engineInstance, taskId)
          );
        } catch (Exception e) {
          log.warn("Failed to retrieve stats for task with id={} from instance={}",
              taskId, engineInstance, e);
        }
      } else {
        log.debug("Got exception={} for test-task id={}", throwable.getMessage(), taskId);
      }
      deleteTask(engineInstance, taskId);

      if (throwable instanceof TimeoutException) {
        counterTimedOut.increment();
        throw new TestTimedOutException("Timed out waiting for test-event-task result", throwable);
      } else if (throwable != null) {
        log.warn("Test-task with id={} completed with unexpected exception", taskId, throwable);
        counterFinishWithException.increment();
        if (throwable instanceof BackendException) {
          // just re-throw since BackendException originates from this service
          throw (BackendException)throwable;
        } else {
          throw new IllegalStateException("Unexpected exception during test-event-task", throwable);
        }
      } else {
        counterSuccess.increment();
        return testTaskResult;
      }
    };

  }

  private void createTask(String tenantId, TestTaskRequest request,
                          EngineInstance engineInstance, String taskId) {
    final String tickScript = tickScriptBuilder
        .build(tenantId, request.getMetric().getName(),
            simplifyTask(request.getTask().getTaskParameters()),
            testEventTaskProperties.getEventHandlerTopic(),
            List.of(TickScriptBuilder.ID_PART_TASK_NAME)
        );

    final Task task = new Task()
        .setId(taskId)
        .setType(Type.stream)
        .setDbrps(Collections.singletonList(new DbRp()
            .setDb(taskId)
            .setRp(InfluxScope.INGEST_RETENTION_POLICY)
        ))
        .setScript(tickScript)
        .setStatus(Status.enabled);

    log.debug("Creating task at instance={} for test with id={}: {}", engineInstance, taskId, task);
    final ResponseEntity<Task> response;
    try {
      response = restTemplate.postForEntity(
          "http://{host}:{port}/kapacitor/v1/tasks",
          task,
          Task.class,
          engineInstance.getHost(), engineInstance.getPort()
      );
    } catch (RestClientException e) {
      counterSetupFailed.increment();
      throw new BackendException(
          null,
          String
              .format("HTTP error while creating task=%s on instance=%s: %s", task, engineInstance,
                  e.getMessage()
              )
      );
    }

    if (response.getStatusCode().isError()) {
      counterSetupFailed.increment();
      String details = response.getBody() != null ? response.getBody().getError() : "";
      throw new BackendException(
          response,
          String
              .format("HTTP error while creating task=%s on instance=%s: %s", task, engineInstance,
                  details
              )
      );
    }
  }

  private void deleteTask(EngineInstance engineInstance,
                          String taskId) {
    log.debug("Deleting task at instance={} for test with id={}", engineInstance, taskId);
    try {
      restTemplate.delete("http://{host}:{port}/kapacitor/v1/tasks/{id}",
          engineInstance.getHost(), engineInstance.getPort(),
          taskId
      );
    } catch (RestClientException e) {
      counterFailedToDelete.increment();
      log.warn("Failed to delete task={} from kapacitorInstance={}", taskId, engineInstance, e);
    }
  }

  private Task.Stats getTaskStats(EngineInstance engineInstance, String taskId) {
    final Task task = restTemplate
        .getForObject("http://{host}:{port}/kapacitor/v1/tasks/{id}",
            Task.class,
            engineInstance.getHost(), engineInstance.getPort(),
            taskId
        );

    log.debug("Getting stats from retrieved task={}", task);

    return task != null ? task.getStats() : null;
  }

  /**
   * Removes all stateful and filtering aspects of the task.
   */
  private static EventEngineTaskParameters simplifyTask(EventEngineTaskParameters taskParameters) {
    return new EventEngineTaskParameters()
        .setLabelSelector(Map.of())
        .setEvalExpressions(taskParameters.getEvalExpressions())
        .setCritical(simplifyLevelExpression(taskParameters.getCritical()))
        .setWarning(simplifyLevelExpression(taskParameters.getWarning()))
        .setInfo(simplifyLevelExpression(taskParameters.getInfo()));
  }

  private static LevelExpression simplifyLevelExpression(LevelExpression levelExpression) {
    return levelExpression != null ? new LevelExpression()
        .setExpression(levelExpression.getExpression())
        .setStateDuration(null)
        : null;
  }

  private void postMetric(TestTaskRequest request, EngineInstance engineInstance,
                          String taskId) {

    final Builder pointBuilder = Point.measurement(request.getMetric().getName());
    if (request.getMetric().getFvalues() != null) {
      request.getMetric().getFvalues().forEach(pointBuilder::addField);
    }
    if (request.getMetric().getIvalues() != null) {
      request.getMetric().getIvalues().forEach(pointBuilder::addField);
    }
    if (request.getMetric().getSvalues() != null) {
      request.getMetric().getSvalues().forEach(pointBuilder::addField);
    }

    final Point point = pointBuilder.build();

    log.debug(
        "Posting metrics to instance={} for test with id={}: {}", engineInstance, taskId, point);

    final ResponseEntity<String> response;
    try {
      final URI kapacitorWriteUri = UriComponentsBuilder
          .fromUriString("http://{host}:{port}/kapacitor/v1/write?db={db}&rp={rp}")
          .buildAndExpand(engineInstance.getHost(), engineInstance.getPort(),
              taskId, InfluxScope.INGEST_RETENTION_POLICY
          )
          .toUri();
      final RequestEntity<String> requestEntity = RequestEntity.post(kapacitorWriteUri)
          .contentType(MediaType.TEXT_PLAIN)
          .body(point.lineProtocol() + "\n");

      response = restTemplate.exchange(requestEntity, String.class);
    } catch (RestClientException e) {
      throw new BackendException(
          null,
          String.format("HTTP error while writing metric=%s on instance=%s: %s", point,
              engineInstance, e.getMessage()
          )
      );
    }

    if (response.getStatusCode().isError()) {
      throw new BackendException(
          response,
          String.format("HTTP error while writing metric=%s on instance=%s: %s", point,
              engineInstance, response.getBody()
          )
      );
    }
  }

  @SuppressWarnings("unused") // used in @KafkaListener
  public String getResultsTopic() {
    return kafkaTopicProperties.getTestEventTaskResults();
  }

  @SuppressWarnings("unused") // used in @KafkaListener
  public String getResultsGroupId() {
    return String.join("-", appName, "testEventTaskResults", ourHostName);
  }

  /**
   * Provide topic-specific deserialization properties to process the "externally" supplied
   * kapacitor event message.
   */
  @SuppressWarnings("unused") // used in @KafkaListener
  public String getConsumerProperties() {
    return String.join(
        "\n",
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG + "=" + JsonDeserializer.class.getName(),
        JsonDeserializer.VALUE_DEFAULT_TYPE + "=" + KapacitorEvent.class.getName(),
        JsonDeserializer.TRUSTED_PACKAGES + "=" + KapacitorEvent.class.getPackageName()
    );
  }

  @KafkaListener(
      topics = "#{__listener.resultsTopic}", groupId = "#{__listener.resultsGroupId}",
      properties = "#{__listener.consumerProperties}")
  public void consumeResultingEvent(KapacitorEvent event) {
    final String taskId = event.getId();
    if (taskId != null) {
      // is it in our pending map?
      final CompletableFuture<TestTaskResult> result = pending.get(taskId);
      if (result != null) {
        counterConsumerConsumed.increment();
        log.debug("Processing result for test-task={} from event={}", taskId, event);
        result.complete(
            new TestTaskResult()
                .setEvent(
                    new EventResult()
                        .setLevel(event.getLevel())
                        .setData(event.getData())
                )
        );
      } else {
        counterConsumerIgnored.increment();
        log.trace("Ignoring test-task-result id={} that isn't ours", taskId);
      }
    } else {
      counterConsumerMissingId.increment();
      log.warn("Task-task-result is missing id: {}", event);
    }
  }
}
