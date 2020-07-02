package com.rackspace.salus.event.manage.model;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.rackspace.salus.telemetry.model.BasicEvalNode;
import com.rackspace.salus.telemetry.model.EvalNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class BasicEvalNodeValidatorTest {

  private LocalValidatorFactoryBean validatorFactoryBean;

  @Before
  public void setUp() {
    validatorFactoryBean = new LocalValidatorFactoryBean();
    validatorFactoryBean.afterPropertiesSet();
  }

  @Test
  public void testValidation_normal() {
    List<String> operands = Arrays.asList("1", "abc", "sigma(1, efg)");
    final EvalNode resource = new BasicEvalNode()
        .setOperands(operands)
        .setOperator("+")
        .setAs("as1");

    final Set<ConstraintViolation<BasicEvalNode>> results = validatorFactoryBean
        .validate((BasicEvalNode) resource);
    assertThat(results, equalTo(Collections.emptySet()));
  }

  @Test
  public void testValidation_Fail() {
    //
    List<String> operands = Arrays.asList("1", "abc", "invalidFunctionName(1, efg)");
    final EvalNode resource = new BasicEvalNode()
        .setOperands(operands)
        .setOperator("+")
        .setAs("as1");

    final Set<ConstraintViolation<BasicEvalNode>> results = validatorFactoryBean
        .validate((BasicEvalNode) resource);
    assertThat(results.size(), equalTo(1));
    final ConstraintViolation<BasicEvalNode> violation = results.iterator().next();
    assertThat(violation.getMessage(), equalTo("Invalid custom metric."));

  }

}
