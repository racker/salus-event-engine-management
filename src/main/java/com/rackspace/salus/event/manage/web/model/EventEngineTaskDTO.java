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

package com.rackspace.salus.event.manage.web.model;


import com.fasterxml.jackson.annotation.JsonView;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.model.View;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class EventEngineTaskDTO {
  UUID id;

  @JsonView(View.Admin.class)
  String tenantId;

  String name;
  String measurement;
  @JsonView(View.Admin.class)
  String kapacitorTaskId;
  EventEngineTaskParameters taskParameters;
  String createdTimestamp;
  String updatedTimestamp;

  public EventEngineTaskDTO(EventEngineTask entity) {
    this.id = entity.getId();
    this.tenantId = entity.getTenantId();
    this.kapacitorTaskId = entity.getKapacitorTaskId();
    this.name = entity.getName();
    this.measurement = entity.getMeasurement();
    this.taskParameters = entity.getTaskParameters();
    this.createdTimestamp = DateTimeFormatter.ISO_INSTANT.format(entity.getCreatedTimestamp());
    this.updatedTimestamp = DateTimeFormatter.ISO_INSTANT.format(entity.getUpdatedTimestamp());
  }
}
