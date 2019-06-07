package com.rackspace.salus.event.manage.model.validator;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.rackspace.salus.event.manage.model.validator.OperandValidator.OperandValidation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;


public class OperandValidator implements ConstraintValidator<OperandValidation, String> {
  public static List<String> validFunctions = Arrays.asList(

      // Stateful functions
      "spread",
      "sigma",
      "count",

      "bool",
      "int",
      "float",
      "string",
      "duration",

      "isPresent",

      "year",
      "month",
      "day",
      "weekday",
      "hour",
      "minute",
      "unixNano",

      "abs",
      "acos",
      "acosh",
      "asin",
      "asinh",
      "atan",
      "atan2",
      "atanh",
      "cbrt",
      "ceil",
      "cos",
      "cosh",
      "erf",
      "erfc",
      "exp",
      "exp2",
      "expm1",
      "floor",
      "gamma",
      "hypot",
      "j0",
      "j1",
      "jn",
      "log",
      "log10",
      "log1p",
      "log2",
      "logb",
      "max",
      "min",
      "mod",
      "pow",
      "pow10",
      "sin",
      "sinh",
      "sqrt",
      "tan",
      "tanh",
      "trunc",
      "y0",
      "y1",
      "yn",


      "strContains",
      "strContainsAny",
      "strCount",
      "strHasPrefix",
      "strHasSuffix",
      "strIndex",
      "strIndexAny",
      "strLastIndex",
      "strLastIndexAny",
      "strReplace",
      "strToLower",
      "strToUpper",
      "strTrim",
      "strTrimLeft",
      "strTrimPrefix",
      "strTrimRight",
      "strTrimSpace",
      "strTrimSuffix",

      "humanBytes");

  public static String functionRegex = "([a-z]+)\\((.*)\\)";
  @Override
  public boolean isValid(String operand, ConstraintValidatorContext context) {
    if (!Pattern.matches(functionRegex, operand)) {
      return true;
    }
    boolean validFunctionName = validFunctions.stream().anyMatch(f -> f.equals(operand));
    return validFunctionName;

  }

  @Target({FIELD, ANNOTATION_TYPE}) // class level constraint
  @Retention(RUNTIME)
  @Constraint(validatedBy = OperandValidator.class) // validator
  @Documented
  public @interface OperandValidation {

    String message() default  "Invalid function name in operand"; // default error message

    Class<?>[] groups() default {}; // required

    Class<? extends Payload>[] payload() default {}; // required
  }
}
