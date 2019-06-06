package com.rackspace.salus.event.manage.model;

import com.rackspace.salus.event.manage.model.validator.ExpressionValidator;
import com.rackspace.salus.event.manage.model.validator.ExpressionValidator.ComparatorValidation;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;


@Data
public class EvalExpression {
  @NotEmpty
  List<String> operands;
  String operator;
  String as;
}
