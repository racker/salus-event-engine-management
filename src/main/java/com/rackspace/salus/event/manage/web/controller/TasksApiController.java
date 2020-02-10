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

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rackspace.salus.event.manage.model.CreateTask;
import com.rackspace.salus.event.manage.services.TasksService;
import com.rackspace.salus.event.manage.web.model.EventEngineTaskDTO;
import com.rackspace.salus.telemetry.entities.EventEngineTask;
import com.rackspace.salus.telemetry.model.PagedContent;
import com.rackspace.salus.common.web.View;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
public class TasksApiController {

  private final TasksService tasksService;
  private final ObjectMapper objectMapper;

  @Autowired
  public TasksApiController(TasksService tasksService, ObjectMapper objectMapper) {
    this.tasksService = tasksService;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/tenant/{tenantId}/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  @ApiOperation(value = "Creates Task for Tenant")
  @ApiResponses(value = { @ApiResponse(code = 201, message = "Successfully Created Task")})
  public EventEngineTaskDTO createTask(@PathVariable String tenantId,
                                       @RequestBody @Validated CreateTask task) {
    final EventEngineTask eventEngineTask = tasksService.createTask(tenantId, task);

    return new EventEngineTaskDTO(eventEngineTask);
  }

  @GetMapping("/tenant/{tenantId}/tasks")
  @ApiOperation(value = "Gets all Tasks for the specific Tenant")
  public PagedContent<EventEngineTaskDTO> getTasks(@PathVariable String tenantId, Pageable pageable) {

    return PagedContent.fromPage(
        tasksService.getTasks(tenantId, pageable)
            .map(EventEngineTaskDTO::new));
  }

  @DeleteMapping("/tenant/{tenantId}/tasks/{taskId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiOperation(value = "Deletes Task for Tenant")
  @ApiResponses(value = { @ApiResponse(code = 204, message = "Task Deleted")})
  public void deleteTask(@PathVariable String tenantId,
                         @PathVariable UUID taskId) {
    tasksService.deleteTask(tenantId, taskId);
  }
}
