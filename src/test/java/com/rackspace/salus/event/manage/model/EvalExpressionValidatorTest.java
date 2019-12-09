package com.rackspace.salus.event.manage.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.EvalExpression;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class EvalExpressionValidatorTest {

  private LocalValidatorFactoryBean validatorFactoryBean;

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValidation_normal() {
    List<String> operands = Arrays.asList("1", "abc", "sigma(1, efg)");
    final EvalExpression resource = new EvalExpression()
        .setOperands(operands)
        .setOperator("+")
        .setAs("as1");

    final Set<ConstraintViolation<EvalExpression>> results = validatorFactoryBean
        .validate(resource);
    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_Fail() {
    //
    List<String> operands = Arrays.asList("1", "abc", "invalidFunctionName(1, efg)");
    final EvalExpression resource = new EvalExpression()
        .setOperands(operands)
        .setOperator("+")
        .setAs("as1");

    final Set<ConstraintViolation<EvalExpression>> results = validatorFactoryBean
        .validate(resource);
    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<EvalExpression> violation = results.iterator().next();
    assertThat(violation.getMessage(), equalTo("Invalid eval expression"));

  }

}
