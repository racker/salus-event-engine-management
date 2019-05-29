package com.rackspace.salus.event.manage.model.validator;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.rackspace.salus.event.manage.model.TaskParameters;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

/**
 * This validator makes sure that at least one parameter level is defined.
 * Tasks can't accomplish anything without any parameters defined because they
 * will have nothing to alert on.
 */
public class TaskParametersValidator implements ConstraintValidator<TaskParametersValidator.AtLeastOneOf, TaskParameters> {

  @Override
  public boolean isValid(TaskParameters parameters, ConstraintValidatorContext context) {
    int count = 0;

    if(parameters.getCritical() == null) {
      count++;
    }
    if(parameters.getInfo() == null) {
      count++;
    }
    if(parameters.getWarning() == null) {
      count++;
    }
    //nothing is set
    if(count == 3) {
      return false;
    } else {
      return true;
    }
  }

  @Target({TYPE, ANNOTATION_TYPE}) // class level constraint
  @Retention(RUNTIME)
  @Constraint(validatedBy = TaskParametersValidator.class) // validator
  @Documented
  public @interface AtLeastOneOf {
    String message() default "At least one of the level expressions must be set"; // default error message

    Class<?>[] groups() default {}; // required

    Class<? extends Payload>[] payload() default {}; // required
  }
}