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

package com.rackspace.salus.event.manage.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rackspace.monplat.protocol.UniversalMetricFrame.MonitoringSystem;
import com.rackspace.salus.event.manage.model.ValidationGroups.Create;
import com.rackspace.salus.event.manage.model.ValidationGroups.Test;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;


@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "monitoringSystem", defaultImpl = GenericTaskCU.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "salus", value= SalusTaskCU.class)
})
public abstract class TaskCU {

  @NotEmpty(groups = Create.class)
  String name;

  @NotNull(groups = {Create.class, Test.class})
  MonitoringSystem monitoringSystem;

  @NotNull(groups = {Create.class, Test.class})
  @Valid
  EventEngineTaskParameters taskParameters;
}
