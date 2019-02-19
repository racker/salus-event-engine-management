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

import com.rackspace.salus.event.common.Tags;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TaskIdGenerator {

  public String generateTaskId(String tenantId, String collectionName) {
    return String.format("%s-%s-%s", tenantId, collectionName, UUID.randomUUID().toString());
  }

  public String pattern(String tenantId) {
    return String.format("%s-*", tenantId);
  }

  public String generateAlertId(String tenantId, String measurement, String field) {
    return String.join(":",
        tenantId,
        String.format("{{index .Tags \"%s\"}}", Tags.RESOURCE_ID),
        measurement,
        field
    );
  }
}
