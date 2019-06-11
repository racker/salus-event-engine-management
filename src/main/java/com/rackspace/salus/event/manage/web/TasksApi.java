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

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.event.manage.entities.EventEngineTask;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.model.CreateTaskResponse;
import com.rackspace.salus.event.manage.model.EventEngineTaskDTO;
import com.rackspace.salus.event.manage.services.TasksService;
import com.rackspace.salus.telemetry.model.PagedContent;
import com.rackspace.salus.telemetry.model.View;
import java.util.UUID;

import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/")
@Api(description = "Monitor operations", authorizations = {
        @Authorization(value = "repose_auth",
                scopes = {
                        @AuthorizationScope(scope = "write:monitor", description = "modify Monitors in your account"),
                        @AuthorizationScope(scope = "read:monitor", description = "read your Monitors"),
                        @AuthorizationScope(scope = "delete:monitor", description = "delete your Monitors")
                })
})
public class TasksApi {

  private final TasksService tasksService;
  private final ObjectMapper objectMapper;

  @Autowired
  public TasksApi(TasksService tasksService, ObjectMapper objectMapper) {
    this.tasksService = tasksService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/tenant/{tenantId}/tasks")
  @ApiOperation(value = "Creates Task for Tenant")
  @ApiResponses(value = { @ApiResponse(code = 201, message = "Successfully Created Task")})
  @JsonView(View.Public.class)
  public CreateTaskResponse createTask(@PathVariable String tenantId,
                                       @RequestBody @Validated CreateTask task) {
    final EventEngineTask eventEngineTask = tasksService.createTask(tenantId, task);

    return objectMapper.convertValue(eventEngineTask, CreateTaskResponse.class);
  }

  @GetMapping("/tenant/{tenantId}/tasks")
  @ApiOperation(value = "Gets all Tasks for the specific Tenant")
  @JsonView(View.Public.class)
  public PagedContent<EventEngineTaskDTO> getTasks(@PathVariable String tenantId, Pageable pageable) {

    return PagedContent.fromPage(
        tasksService.getTasks(tenantId, pageable)
            .map(eventEngineTask -> objectMapper.convertValue(eventEngineTask, EventEngineTaskDTO.class)));
  }

  @DeleteMapping("/tenant/{tenantId}/tasks/{taskId}")
  @ApiOperation(value = "Deletes Task for Tenant")
  @ApiResponses(value = { @ApiResponse(code = 204, message = "Task Deleted")})
  @JsonView(View.Public.class)
  public void deleteTask(@PathVariable String tenantId,
                         @PathVariable UUID taskId) {
    tasksService.deleteTask(tenantId, taskId);
  }
}
