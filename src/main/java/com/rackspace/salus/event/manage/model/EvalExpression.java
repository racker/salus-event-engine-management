package com.rackspace.salus.event.manage.model;

import com.rackspace.salus.event.manage.model.validator.EvalExpressionValidator.EvalExpressionValidation;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Data;


@Data
@EvalExpressionValidation
public class EvalExpression {

  @NotEmpty
  List<String> operands;
  @NotBlank
  String operator;
  @NotBlank
  String as;
}
