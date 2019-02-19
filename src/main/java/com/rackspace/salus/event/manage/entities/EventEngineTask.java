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

package com.rackspace.salus.event.manage.entities;

import com.rackspace.salus.event.manage.model.scenarios.Scenario;
import com.vladmihalcea.hibernate.type.json.JsonStringType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

@Entity
@Table(
    indexes = {
        @Index(name = "tenant_measures", columnList = "tenantId,measurement"),
        @Index(name = "assigned_partition", columnList = "assignedPartition")
    }
)
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Data
public class EventEngineTask {

  @Id @GeneratedValue
  Long id;

  @Column(nullable = false)
  String tenantId;

  @Column(nullable = false)
  String measurement;

  @Column(nullable = false)
  String taskId;

  int assignedPartition;

  @Column(columnDefinition = "text")
  String computedTickScript;

  @Type(type = "json")
  @Column(nullable = false, columnDefinition = "json")
  Scenario scenario;
}
