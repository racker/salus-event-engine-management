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

import java.util.UUID;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class KapacitorTaskIdGenerator {
  private static final String UNDERSCORE = "_";
  private static final String COLON = ":";

  @Data
  public static class KapacitorTaskId {
    UUID baseId;
    String kapacitorTaskId;
  }

  public KapacitorTaskId generateTaskId(String tenantId, String collectionName) {
    final KapacitorTaskId taskId = new KapacitorTaskId()
        .setBaseId(UUID.randomUUID());
    taskId.setKapacitorTaskId(String.format("%s-%s-%s",
        tenantId.replace(COLON, UNDERSCORE), collectionName, taskId.getBaseId().toString()
    ));

    return taskId;
  }

  public KapacitorTaskId updateTaskId(String tenantId, String collectionName, UUID uuid) {
    final KapacitorTaskId taskId = new KapacitorTaskId()
        .setBaseId(uuid);
    taskId.setKapacitorTaskId(String.format("%s-%s-%s",
        tenantId.replace(COLON, UNDERSCORE), collectionName, taskId.getBaseId().toString()
    ));

    return taskId;
  }
}
