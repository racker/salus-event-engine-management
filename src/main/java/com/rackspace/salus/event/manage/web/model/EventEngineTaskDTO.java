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


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import com.rackspace.monplat.protocol.UniversalMetricFrame;
import com.rackspace.monplat.protocol.UniversalMetricFrame.MonitoringSystem;
import com.rackspace.salus.common.web.View;
import com.rackspace.salus.event.manage.model.ValidationGroups.Create;
import com.rackspace.salus.event.manage.model.ValidationGroups.Test;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "monitoringSystem", defaultImpl = GenericEventEngineTaskDTO.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "salus", value = SalusEventEngineTaskDTO.class)
})
@Data @NoArgsConstructor
public abstract class EventEngineTaskDTO {
  UUID id;

  @JsonView(View.Admin.class)
  String tenantId;

  String name;
  String measurement;
  EventEngineTaskParameters taskParameters;
  String createdTimestamp;
  String updatedTimestamp;
  @NotNull(groups = {Create.class, Test.class})
  UniversalMetricFrame.MonitoringSystem monitoringSystem;

  public EventEngineTaskDTO(EventEngineTask entity) {
    this.id = entity.getId();
    this.monitoringSystem = MonitoringSystem.valueOf(entity.getMonitoringSystem());
    this.tenantId = entity.getTenantId();
    this.name = entity.getName();
    this.taskParameters = entity.getTaskParameters();
    this.createdTimestamp = DateTimeFormatter.ISO_INSTANT.format(entity.getCreatedTimestamp());
    this.updatedTimestamp = DateTimeFormatter.ISO_INSTANT.format(entity.getUpdatedTimestamp());
  }
}
