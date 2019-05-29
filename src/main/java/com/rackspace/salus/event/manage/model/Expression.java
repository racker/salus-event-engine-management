package com.rackspace.salus.event.manage.model;

import com.rackspace.salus.event.manage.model.validator.ExpressionValidator;
import com.rackspace.salus.event.manage.model.validator.ExpressionValidator.ComparatorValidation;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data

public class Expression {
  @NotEmpty
  String field;
  @NotNull
  Number threshold;
  @NotEmpty
  @ExpressionValidator.ComparatorValidation()
  String comparator;

}
