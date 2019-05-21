package com.rackspace.salus.event.manage.model;

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
  String comparator;

}
