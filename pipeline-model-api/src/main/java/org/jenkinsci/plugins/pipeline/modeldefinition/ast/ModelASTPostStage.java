package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents a list of {@code BuildCondition} and {@code StepsBlock} pairs to be called, depending
 * on whether the build condition is satisfied, at the end of the stage.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
public final class ModelASTPostStage extends ModelASTBuildConditionsContainer {
  public ModelASTPostStage(java.lang.Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  public java.lang.String getName() {
    return "post";
  }

  @Override
  public String toString() {
    return "ModelASTPostStage{" + "conditions=" + getConditions() + "}";
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    validator.validateElement(this);
    super.validate(validator);
  }
}
