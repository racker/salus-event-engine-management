package com.rackspace.salus.event.manage.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.ComparisonExpression;
import java.util.Collections;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class ComparisonExpressionValidatorTest {
  private LocalValidatorFactoryBean validatorFactoryBean;


  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }


  @Test
  public void testValidation_equals() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("==")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_greaterEquals() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator(">=")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_lessEquals() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("<=")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_greater() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator(">")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_less() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("<")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_notEqual() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("!=")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_regex() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("=~")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_notRegex() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("!~")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_failure() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("thisShouldn'tWork")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<ComparisonExpression> violation = results.iterator().next();
    assertThat(violation.getPropertyPath().toString(), equalTo("comparator"));
    assertThat(violation.getMessage(),
        equalTo("Valid comparators are: ==, !=, >, >=, <, <=, =~, !~"));
  }

  @Test
  public void testValidation_failure2() {

    final ComparisonExpression resource = new ComparisonExpression()
        .setComparator("=")
        .setMetricName("used")
        .setComparisonValue(33);
    final Set<ConstraintViolation<ComparisonExpression>> results = validatorFactoryBean.validate(resource);

    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<ComparisonExpression> violation = results.iterator().next();
    assertThat(violation.getPropertyPath().toString(), equalTo("comparator"));
    assertThat(violation.getMessage(),
        equalTo("Valid comparators are: ==, !=, >, >=, <, <=, =~, !~"));
  }

}
