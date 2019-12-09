package com.rackspace.salus.event.manage.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.Expression;
import com.rackspace.salus.telemetry.entities.EventEngineTaskParameters.LevelExpression;
import java.util.Collections;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class TaskParametersValidatorTest {

  private LocalValidatorFactoryBean validatorFactoryBean;


  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValidation_normal_critical() {
    final EventEngineTaskParameters resource = new EventEngineTaskParameters()
        .setCritical(new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<EventEngineTaskParameters>> results = validatorFactoryBean.validate(resource);
    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_normal_warning() {

    final EventEngineTaskParameters resource = new EventEngineTaskParameters()
        .setWarning(new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<EventEngineTaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_normal_info() {

    final EventEngineTaskParameters resource = new EventEngineTaskParameters()
        .setInfo(new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<EventEngineTaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_normal_multiple() {

    final EventEngineTaskParameters resource = new EventEngineTaskParameters()
        .setInfo(new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)))
        .setWarning(new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)))
        .setCritical(new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<EventEngineTaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_Fail() {

    final EventEngineTaskParameters resource = new EventEngineTaskParameters();
    final Set<ConstraintViolation<EventEngineTaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<EventEngineTaskParameters> violation = results.iterator().next();
    assertThat(violation.getPropertyPath().toString(), equalTo(""));
    assertThat(violation.getMessage(), equalTo("At least one of the level expressions must be set"));
  }

}
