package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import hudson.model.Result;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/** @author Kohsuke Kawaguchi */
public class GroovyShellDecoratorImplTest {
  @Rule public final JenkinsRule j = new JenkinsRule();

  /**
   * Not a syntax error but semantics error in using the 'pipeline' step. This should be reported
   * nicely.
   */
  @Test
  public void errorInJenkinsfile() throws Exception {
    WorkflowJob job = j.createProject(WorkflowJob.class);
    // still a syntactically valid groovy code but no stage name
    job.setDefinition(
        new CpsFlowDefinition("pipeline { stages { stage { sh './test.sh' } } }", true));
    WorkflowRun b = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());

    j.assertLogContains(Messages.ModelParser_ExpectedStringLiteral(), b);
    j.assertLogContains(
        String.format(
            "   pipeline { stages { stage { sh './test.sh' } } }%n"
                + "                             ^%n"),
        b);
  }

  /** V1 files shouldn't be affected */
  @Test
  public void v1() throws Exception {
    WorkflowJob job = j.createProject(WorkflowJob.class);
    // still a syntactically valid groovy code but no stage name
    job.setDefinition(new CpsFlowDefinition("node { echo 'hello' }", true));
    j.assertBuildStatusSuccess(job.scheduleBuild2(0));
  }
}
