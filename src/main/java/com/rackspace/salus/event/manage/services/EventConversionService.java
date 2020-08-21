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

import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventConversionService {

  public EventEngineTask convertFromInput(String tenantId, UUID uuid, CreateTask in) {
    EventEngineTask eventEngineTask = new EventEngineTask()
        .setId(uuid)
        .setMeasurement(in.getMeasurement())
        .setTaskParameters(in.getTaskParameters())
        .setName(in.getName())
        .setTenantId(tenantId);
    return eventEngineTask;
  }
}
