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

import com.rackspace.salus.event.manage.model.validator.TaskParametersValidator;
import com.rackspace.salus.telemetry.model.ValidLabelKeys;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.Data;

@Data
@TaskParametersValidator.AtLeastOneOf()
public class TaskParameters {

  @Valid
  LevelExpression info;
  @Valid
  LevelExpression warning;
  @Valid
  LevelExpression critical;

  @Valid
  List<EvalExpression> evalExpressions;

  Integer windowLength;
  List<String> windowFields;

  boolean flappingDetection;

  @ValidLabelKeys
  Map<String, String> labelSelector;

  @Data
  public static class LevelExpression {

    @Valid
    Expression expression;
    Integer consecutiveCount;
  }
}
