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

package com.rackspace.salus.event.manage.model.kapacitor;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class Var {
  Object value;
  Type type;

  public enum Type {
    // can't use lowercase as identifier since those are Java keywords
    @JsonProperty("bool")
    BOOL,
    @JsonProperty("int")
    INT,
    @JsonProperty("float")
    FLOAT,
    @JsonProperty("string")
    STRING
  }

  public static Var from(Object value) {
    final VarBuilder builder = Var.builder()
        .value(value);
    if (value instanceof Integer) {
      builder.type(Type.INT);
    } else if (value instanceof Float) {
      builder.type(Type.FLOAT);
    } else if (value instanceof Boolean) {
      builder.type(Type.BOOL);
    } else if (value instanceof String) {
      builder.type(Type.STRING);
    } else {
      throw new IllegalArgumentException("Unable to handle value with type " + value.getClass());
    }
    return builder.build();
  }
}
