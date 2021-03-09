package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class AbstractConditionalWithChildrenTest {

  @Issue("JENKINS-51931")
  @Test
  public void conditionalWithChildrenNotNull() {
    AbstractConditionalWithChildren<AllOfConditional> allOfConditional = new AllOfConditional(null);
    List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> allOfChildren =
        allOfConditional.getChildren();
    assertNotNull(allOfChildren);
    assertTrue(allOfChildren.isEmpty());

    AbstractConditionalWithChildren<AnyOfConditional> anyOfConditional = new AnyOfConditional(null);
    List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> anyOfChildren =
        anyOfConditional.getChildren();
    assertNotNull(anyOfChildren);
    assertTrue(anyOfChildren.isEmpty());
  }
}
