package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.junit.Test;

public class ValidateDeclarativePipelineStepTest extends AbstractModelDefTest {

  @Test
  public void passes() throws Exception {
    expect("steps/validateDeclarativePipelineStep")
        .otherResource("simplePipeline.groovy", "testFile.groovy")
        .logContains(
            "Declarative Pipeline file 'testFile.groovy' is valid.", "validation result - true")
        .go();
  }

  @Test
  public void noFile() throws Exception {
    expect("steps/validateDeclarativePipelineStep")
        .logContains(
            "Declarative Pipeline file 'testFile.groovy' does not exist.",
            "validation result - false")
        .go();
  }

  @Test
  public void noPipelineStep() throws Exception {
    expect("steps/validateDeclarativePipelineStep")
        .otherResource("steps/validateDeclarativePipelineStep.groovy", "testFile.groovy")
        .logContains(
            "Declarative Pipeline file 'testFile.groovy' does not contain the 'pipeline' step.",
            "validation result - false")
        .go();
  }

  @Test
  public void validationErrors() throws Exception {
    expect("steps/validateDeclarativePipelineStep")
        .otherResource("errors/emptyEnvironment.groovy", "testFile.groovy")
        .logContains(
            "Error(s) validating Declarative Pipeline file 'testFile.groovy' - org.codehaus.groovy.control.MultipleCompilationErrorsException",
            "WorkflowScript: 26: No variables specified for environment @ line 26, column 5.",
            "validation result - false")
        .go();
  }
}
