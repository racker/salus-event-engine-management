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

import static com.rackspace.salus.common.web.RemoteOperations.mapRestClientExceptions;
import com.rackspace.salus.event.manage.model.TestTaskRequest;
import com.rackspace.salus.event.manage.model.TestTaskResult;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;


public class EventTaskApiClient implements EventTaskApi {

    private final RestTemplate restTemplate;
    private static final String SERVICE_NAME = "event-engine-management";

    public EventTaskApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public TestTaskResult performTestTask(String tenantId, TestTaskRequest request) {
        String uriString = UriComponentsBuilder
                .fromUriString("/api/tenant/{tenantId}/test-task")
                .buildAndExpand(tenantId)
                .toUriString();

        return mapRestClientExceptions(
                SERVICE_NAME,
                () -> restTemplate.postForEntity(
                        uriString,
                        new HttpEntity<>(request),
                    TestTaskResult.class
                ).getBody());
    }
}
