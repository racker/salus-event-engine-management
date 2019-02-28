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

package com.rackspace.salus.event.manage.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.event.manage.entities.EventEngineTask;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.model.CreateTaskResponse;
import com.rackspace.salus.event.manage.model.EventEngineTaskDTO;
import com.rackspace.salus.event.manage.services.TasksService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TasksApi {

  private final TasksService tasksService;
  private final ObjectMapper objectMapper;

  @Autowired
  public TasksApi(TasksService tasksService, ObjectMapper objectMapper) {
    this.tasksService = tasksService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("{tenantId}")
  public CreateTaskResponse createTask(@PathVariable String tenantId,
                                       @RequestBody @Validated CreateTask task) {
    final EventEngineTask eventEngineTask = tasksService.createTask(tenantId, task);

    return objectMapper.convertValue(eventEngineTask, CreateTaskResponse.class);
  }

  @GetMapping("{tenantId}")
  public List<EventEngineTaskDTO> getTasks(@PathVariable String tenantId) {

    return tasksService.getTasks(tenantId).stream()
        .map(eventEngineTask -> objectMapper.convertValue(eventEngineTask, EventEngineTaskDTO.class))
        .collect(Collectors.toList());
  }

  @GetMapping("{tenantId}/{measurement}")
  public List<EventEngineTaskDTO> getTasksByMeasurement(@PathVariable String tenantId,
                                                        @PathVariable String measurement) {

    return tasksService.getTasks(tenantId, measurement).stream()
        .map(eventEngineTask -> objectMapper.convertValue(eventEngineTask, EventEngineTaskDTO.class))
        .collect(Collectors.toList());
  }

  @DeleteMapping("{tenantId}/{taskId}")
  public void deleteTask(@PathVariable String tenantId,
                         @PathVariable long taskId) {
    tasksService.deleteTask(tenantId, taskId);
  }
}
