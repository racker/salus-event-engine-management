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

package com.rackspace.salus.event.manage.errors;

import org.springframework.http.ResponseEntity;

public class BackendException extends RuntimeException {

  private final ResponseEntity<?> response;

  public BackendException(ResponseEntity<?> response, String message) {
    super(message);
    this.response = response;
  }

  public ResponseEntity<?> getResponse() {
    return response;
  }
}