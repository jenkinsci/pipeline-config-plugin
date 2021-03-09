package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents the special step for {@code ScriptStep}, which are executed without validation against
 * the declarative subset.
 *
 * @author Andrew Bayer
 */
public class ModelASTScriptBlock extends AbstractModelASTCodeBlock {
  public ModelASTScriptBlock(Object sourceLocation) {
    super(sourceLocation, "script");
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
  }
}
