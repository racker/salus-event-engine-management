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

package com.rackspace.salus.event.manage.web.client;

import static com.rackspace.salus.common.util.SpringResourceUtils.readContent;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.model.TestTaskRequest;
import com.rackspace.salus.event.manage.model.TestTaskResult;
import com.rackspace.salus.event.manage.model.TestTaskResult.EventResult;
import com.rackspace.salus.event.manage.model.kapacitor.KapacitorEvent.EventData;
import com.rackspace.salus.event.manage.model.kapacitor.KapacitorEvent.SeriesItem;
import com.rackspace.salus.event.manage.model.kapacitor.Task.Stats;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@RestClientTest
public class EventTaskApiClientTest {

  @TestConfiguration
  public static class ExtraTestConfig {

    @Bean
    public EventTaskApiClient eventTaskApiClient(RestTemplateBuilder restTemplateBuilder) {
      return new EventTaskApiClient(restTemplateBuilder.build());
    }
  }

  private PodamFactory podamFactory = new PodamFactoryImpl();

  @Autowired
  MockRestServiceServer mockServer;

  @Autowired
  EventTaskApiClient eventTaskApiClient;

  @Autowired
  ObjectMapper objectMapper;

  @MockBean
  TenantMetadataRepository tenantMetadataRepository;

  @MockBean
  EventEnginePicker eventEnginePicker;

  @Test
  public void testPerformTestTask() throws IOException {
    TestTaskRequest testTaskRequest = objectMapper
        .readValue(readContent("EventTaskApiClientTest/testPerformTestTask_req.json"),
            TestTaskRequest.class);

    String tenantId = RandomStringUtils.randomAlphabetic(8);
    final TestTaskResult testTaskResultExpected = new TestTaskResult()
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
        );

    mockServer.expect(requestToUriTemplate("/api/tenant/{tenantId}/test-task", tenantId))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json(objectMapper.writeValueAsString(testTaskRequest)))
        .andRespond(
            withSuccess(objectMapper.writeValueAsString(testTaskResultExpected),
                MediaType.APPLICATION_JSON));

    TestTaskResult testTaskResultActual = eventTaskApiClient
        .performTestTask(tenantId, testTaskRequest);
    assertThat(testTaskResultExpected, equalTo(testTaskResultActual));
  }
}
