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

import com.rackspace.salus.event.model.kapacitor.KapacitorEvent;
import com.rackspace.salus.event.model.kapacitor.Task;
import java.util.List;
import lombok.Data;

@Data
public class TestTaskResult {

  private List<String> errors;
  private TestTaskResultData data;

  @Data
  public static class TestTaskResultData {

    /**
     * Contains the resulting event per metric provided in the request.
     */
    List<EventResult> events;
    /**
     * Contains the kapacitor stats for task that was created.
     */
    Task.Stats stats;

    @Data
    public static class EventResult {

      String level;
      KapacitorEvent.EventData data;
    }
  }


}
