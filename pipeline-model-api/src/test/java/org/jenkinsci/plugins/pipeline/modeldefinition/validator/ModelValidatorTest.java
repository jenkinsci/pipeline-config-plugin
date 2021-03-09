package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import java.util.Collections;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

public class ModelValidatorTest {

  @Test
  public void ensure_postBuild_calls_own_validator_and_super() {
    ModelValidator validator = Mockito.mock(ModelValidator.class);
    ModelASTBuildCondition condition = new ModelASTBuildCondition(null);
    ModelASTBranch branch = new ModelASTBranch(null);
    condition.setBranch(branch);
    ModelASTPostBuild instance = new ModelASTPostBuild(null);
    instance.setConditions(Collections.singletonList(condition));
    instance.validate(validator);
    InOrder inOrder = Mockito.inOrder(validator);
    inOrder.verify(validator, Mockito.times(1)).validateElement(instance);
    inOrder
        .verify(validator, Mockito.times(1))
        .validateElement((ModelASTBuildConditionsContainer) instance);
    inOrder.verify(validator, Mockito.times(1)).validateElement(condition);
    inOrder.verify(validator, Mockito.times(1)).validateElement(branch);
  }

  @Test
  public void ensure_postStage_calls_own_validator_and_super() {
    ModelValidator validator = Mockito.mock(ModelValidator.class);
    ModelASTBuildCondition condition = new ModelASTBuildCondition(null);
    ModelASTBranch branch = new ModelASTBranch(null);
    condition.setBranch(branch);
    ModelASTPostStage instance = new ModelASTPostStage(null);
    instance.setConditions(Collections.singletonList(condition));
    instance.validate(validator);
    InOrder inOrder = Mockito.inOrder(validator);
    inOrder.verify(validator, Mockito.times(1)).validateElement(instance);
    inOrder
        .verify(validator, Mockito.times(1))
        .validateElement((ModelASTBuildConditionsContainer) instance);
    inOrder.verify(validator, Mockito.times(1)).validateElement(condition);
    inOrder.verify(validator, Mockito.times(1)).validateElement(branch);
  }

  @Test
  public void triggerValidateCallsRightMethod() throws Exception {
    ModelValidator validator = Mockito.mock(ModelValidator.class);

    ModelASTTrigger trigger = new ModelASTTrigger(null);
    ModelASTMethodArg arg = ModelASTValue.fromConstant("bar", null);
    trigger.setArgs(Collections.singletonList(arg));
    trigger.setName("foo");
    trigger.validate(validator);
    InOrder inOrder = Mockito.inOrder(validator);
    inOrder.verify(validator, Mockito.times(1)).validateElement(trigger);
    inOrder.verify(validator, Mockito.times(1)).validateElement((ModelASTMethodCall) trigger);
  }
}
