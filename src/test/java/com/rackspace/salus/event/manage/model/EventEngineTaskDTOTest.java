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

package com.rackspace.salus.event.manage.model;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.event.manage.entities.EventEngineTask;
import com.rackspace.salus.telemetry.model.View;
import org.junit.Test;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

public class EventEngineTaskDTOTest {
  final PodamFactory podamFactory = new PodamFactoryImpl();

  final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void testFieldsCovered() throws Exception {
    final EventEngineTask task = podamFactory.manufacturePojo(EventEngineTask.class);

    final EventEngineTaskDTO dto = task.toDTO();

    assertThat(dto.getId(), notNullValue());
    assertThat(dto.getTenantId(), notNullValue());
    assertThat(dto.getTaskId(), notNullValue());
    assertThat(dto.getName(), notNullValue());
    assertThat(dto.getMeasurement(), notNullValue());
    assertThat(dto.getTaskParameters(), notNullValue());
    assertThat(dto.getCreatedTimestamp(), notNullValue());
    assertThat(dto.getUpdatedTimestamp(), notNullValue());

    assertThat(dto.getId(), equalTo(task.getId()));
    assertThat(dto.getTenantId(), equalTo(task.getTenantId()));
    assertThat(dto.getTaskId(), equalTo(task.getTaskId()));
    assertThat(dto.getName(), equalTo(task.getName()));
    assertThat(dto.getMeasurement(), equalTo(task.getMeasurement()));
    assertThat(dto.getTaskParameters(), equalTo(task.getTaskParameters()));
    assertThat(dto.getCreatedTimestamp(), equalTo(task.getCreatedTimestamp().toString()));
    assertThat(dto.getUpdatedTimestamp(), equalTo(task.getUpdatedTimestamp().toString()));

    String objectAsString;
    EventEngineTaskDTO convertedDto;

    objectAsString = objectMapper.writerWithView(View.Public.class).writeValueAsString(dto);
    convertedDto = objectMapper.readValue(objectAsString, EventEngineTaskDTO.class);
    assertThat(convertedDto.getTenantId(), nullValue());

    objectAsString = objectMapper.writerWithView(View.Admin.class).writeValueAsString(dto);
    convertedDto = objectMapper.readValue(objectAsString, EventEngineTaskDTO.class);
    assertThat(convertedDto.getTenantId(), notNullValue());

    objectAsString = objectMapper.writerWithView(View.Internal.class).writeValueAsString(dto);
    convertedDto = objectMapper.readValue(objectAsString, EventEngineTaskDTO.class);
    assertThat(convertedDto.getTenantId(), nullValue());
  }
}