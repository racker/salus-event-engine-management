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


import com.rackspace.salus.telemetry.entities.subtype.SalusEventEngineTask;
import com.rackspace.salus.telemetry.model.ConfigSelectorScope;
import com.rackspace.salus.telemetry.model.MonitorType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class SalusEventEngineTaskDTO extends EventEngineTaskDTO {
  MonitorType monitorType;
  ConfigSelectorScope monitorScope;

  public SalusEventEngineTaskDTO(SalusEventEngineTask entity) {
    super(entity);
    this.monitorType = entity.getMonitorType();
    this.monitorScope = entity.getMonitorScope();
  }
}
