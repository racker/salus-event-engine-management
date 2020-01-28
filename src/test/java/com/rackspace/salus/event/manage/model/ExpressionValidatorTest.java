package com.rackspace.salus.event.manage.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Expression;
import java.util.Collections;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class ExpressionValidatorTest {
  private LocalValidatorFactoryBean validatorFactoryBean;


  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }


  @Test
  public void testValidation_equals() {

    final Expression resource = new Expression()
        .setComparator("==")
        .setField("used")
        .setThreshold(33);
    final Set<ConstraintViolation<Expression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_greaterEquals() {

    final Expression resource = new Expression()
        .setComparator(">=")
        .setField("used")
        .setThreshold(33);
    final Set<ConstraintViolation<Expression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_lessEquals() {

    final Expression resource = new Expression()
        .setComparator("<=")
        .setField("used")
        .setThreshold(33);
    final Set<ConstraintViolation<Expression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_greater() {

    final Expression resource = new Expression()
        .setComparator(">")
        .setField("used")
        .setThreshold(33);
    final Set<ConstraintViolation<Expression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_less() {

    final Expression resource = new Expression()
        .setComparator("<")
        .setField("used")
        .setThreshold(33);
    final Set<ConstraintViolation<Expression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_failure() {

    final Expression resource = new Expression()
        .setComparator("thisShouldn'tWork")
        .setField("used")
        .setThreshold(33);
    final Set<ConstraintViolation<Expression>> results = validatorFactoryBean.validate(resource);

    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<Expression> violation = results.iterator().next();
    assertThat(violation.getPropertyPath().toString(), equalTo("comparator"));
    assertThat(violation.getMessage(), equalTo("Valid comparators are: ==, >, >=, <, <="));
  }

  @Test
  public void testValidation_failure2() {

    final Expression resource = new Expression()
        .setComparator("=")
        .setField("used")
        .setThreshold(33);
    final Set<ConstraintViolation<Expression>> results = validatorFactoryBean.validate(resource);

    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<Expression> violation = results.iterator().next();
    assertThat(violation.getPropertyPath().toString(), equalTo("comparator"));
    assertThat(violation.getMessage(), equalTo("Valid comparators are: ==, >, >=, <, <="));
  }

}
