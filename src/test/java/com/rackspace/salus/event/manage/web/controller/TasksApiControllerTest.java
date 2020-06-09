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

package com.rackspace.salus.event.manage.web.controller;

import static com.rackspace.salus.test.WebTestUtils.validationError;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.event.discovery.EventEnginePicker;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.services.TasksService;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Expression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LevelExpression;
import com.rackspace.salus.telemetry.repositories.TenantMetadataRepository;
import com.rackspace.salus.telemetry.web.TenantVerification;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import uk.co.jemos.podam.api.DefaultClassInfoStrategy;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = TasksApiController.class)
public class TasksApiControllerTest {

  // Ensure Expressions have their `threshold` field populated with something (a string).
  DefaultClassInfoStrategy classInfoStrategy;
  {
    try {
      classInfoStrategy = (DefaultClassInfoStrategy) DefaultClassInfoStrategy.getInstance()
          .addExtraMethod(Expression.class, "podamHelper", String.class);
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }
  PodamFactory podamFactory = new PodamFactoryImpl();

  @Autowired
  MockMvc mockMvc;

  @MockBean
  TasksService tasksService;

  @MockBean
  EventEnginePicker eventEnginePicker;

  @MockBean
  TenantMetadataRepository tenantMetadataRepository;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  public void testTenantVerification_Success() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic( 8 );

    doNothing().when(tasksService).deleteTask(anyString(), any());
    when(tenantMetadataRepository.existsByTenantId(tenantId))
        .thenReturn(true);

    mockMvc.perform(delete("/api/tenant/{tenantId}/tasks/{name}", tenantId, UUID.randomUUID())
        // header must be set to trigger tenant verification
        .header(TenantVerification.HEADER_TENANT, tenantId))
        .andExpect(status().isNoContent());

    verify(tenantMetadataRepository).existsByTenantId(tenantId);
  }

  @Test
  public void testTenantVerification_Fail() throws Exception {
    String tenantId = RandomStringUtils.randomAlphabetic( 8 );

    doNothing().when(tasksService).deleteTask(anyString(), any());
    when(tenantMetadataRepository.existsByTenantId(tenantId))
        .thenReturn(false);

    mockMvc.perform(delete("/api/tenant/{tenantId}/tasks/{name}", tenantId, UUID.randomUUID())
        // header must be set to trigger tenant verification
        .header(TenantVerification.HEADER_TENANT, tenantId))
        .andExpect(status().isNotFound())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message", is(TenantVerification.ERROR_MSG)));

    verify(tenantMetadataRepository).existsByTenantId(tenantId);
  }

  @Test
  public void testGetTasks() throws Exception {
    int numberOfTasks = 20;
    // Use the APIs default Pageable settings
    int page = 0;
    int pageSize = 20;
    List<EventEngineTask> tasks = new ArrayList<>();
    for (int i = 0; i < numberOfTasks; i++) {
      tasks.add(podamFactory.manufacturePojo(EventEngineTask.class));
    }

    int start = page * pageSize;
    int end = numberOfTasks;
    Page<EventEngineTask> pageOfTasks = new PageImpl<>(tasks.subList(start, end),
        PageRequest.of(page, pageSize),
        numberOfTasks);

    when(tasksService.getTasks(anyString(), any()))
        .thenReturn(pageOfTasks);

    String tenantId = RandomStringUtils.randomAlphabetic( 8 );

    mockMvc.perform(get("/api/tenant/{tenantId}/tasks", tenantId)
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content.*", hasSize(numberOfTasks)))
        .andExpect(jsonPath("$.totalPages", equalTo(1)))
        .andExpect(jsonPath("$.totalElements", equalTo(numberOfTasks)));

    verify(tasksService).getTasks(tenantId, PageRequest.of(0, 20));
    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testCreateTask() throws Exception{
    EventEngineTask task = podamFactory.manufacturePojo(EventEngineTask.class);
    when(tasksService.createTask(anyString(), any()))
        .thenReturn(task);

    String tenantId = RandomStringUtils.randomAlphabetic( 8 );

    CreateTask create = buildCreateTask(true);

    mockMvc.perform(post("/api/tenant/{tenantId}/tasks", tenantId)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(content()
            .contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

    verify(tasksService).createTask(tenantId, create);
    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testCreateTask_MissingName() throws Exception{
    EventEngineTask task = podamFactory.manufacturePojo(EventEngineTask.class);
    when(tasksService.createTask(anyString(), any()))
        .thenReturn(task);

    String tenantId = RandomStringUtils.randomAlphabetic( 8 );

    CreateTask create = buildCreateTask(false);

    mockMvc.perform(post("/api/tenant/{tenantId}/tasks", tenantId)
        .content(objectMapper.writeValueAsString(create))
        .contentType(MediaType.APPLICATION_JSON)
        .characterEncoding(StandardCharsets.UTF_8.name()))
        .andDo(print())
        .andExpect(status().isBadRequest())
        .andExpect(validationError("name", "must not be empty"));

    verifyNoMoreInteractions(tasksService);
  }

  @Test
  public void testDeleteTask() throws Exception {
    doNothing().when(tasksService).deleteTask(anyString(), any());

    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
    mockMvc.perform(delete(
      "/api/tenant/{tenantId}/tasks/{name}",
      "t-1", id))
      .andDo(print())
      .andExpect(status().isNoContent());

    verify(tasksService).deleteTask("t-1", id);
    verifyNoMoreInteractions(tasksService);
  }

  private static CreateTask buildCreateTask(boolean setName) {
    CreateTask task = new CreateTask()
        .setMeasurement("cpu")
        .setTaskParameters(
            new EventEngineTaskParameters()
                .setLabelSelector(
                    singletonMap("agent_environment", "localdev")
                )
                .setCritical(
                    new LevelExpression()
                        .setStateDuration(1)
                        .setExpression(
                            new Expression()
                                .setField("usage_user")
                                .setComparator(">")
                                .setThreshold(75)
                        )
                )
        );

    if (setName) {
      task.setName("this is my name");
    }
    return task;
  }
}
