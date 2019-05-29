package com.rackspace.salus.event.manage.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.event.manage.model.TaskParameters.LevelExpression;
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

    final TaskParameters resource = new TaskParameters()
        .setCritical(new TaskParameters().new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<TaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_normal_warning() {

    final TaskParameters resource = new TaskParameters()
        .setWarning(new TaskParameters().new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<TaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_normal_info() {

    final TaskParameters resource = new TaskParameters()
        .setInfo(new TaskParameters().new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<TaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_normal_multiple() {

    final TaskParameters resource = new TaskParameters()
        .setInfo(new TaskParameters().new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)))
        .setWarning(new TaskParameters().new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)))
        .setCritical(new TaskParameters().new LevelExpression()
            .setConsecutiveCount(5)
            .setExpression(new Expression()
                .setComparator(">")
                .setField("used")
                .setThreshold(33)));
    final Set<ConstraintViolation<TaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_Fail() {

    final TaskParameters resource = new TaskParameters();
    final Set<ConstraintViolation<TaskParameters>> results = validatorFactoryBean.validate(resource);

    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<TaskParameters> violation = results.iterator().next();
    assertThat(violation.getPropertyPath().toString(), equalTo(""));
    assertThat(violation.getMessage(), equalTo("At least one of the level expressions must be set"));
  }

}
