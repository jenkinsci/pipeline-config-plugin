/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.pipeline.modeldefinition;

import static org.jenkinsci.plugins.pipeline.modeldefinition.BasicModelDefTest.syntheticStagePredicate;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;
import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class SyntheticStageGraphListenerTest {

  @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

  @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();

  @Issue("JENKINS-41243")
  @Test
  public void resume() throws Exception {
    rr.addStep(
        new Statement() {
          @Override
          public void evaluate() throws Throwable {
            WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
            p.setDefinition(
                new CpsFlowDefinition(
                    "pipeline {\n"
                        + "  agent any\n"
                        + "  stages {\n"
                        + "    stage('x') {\n"
                        + "      steps {\n"
                        + "        script {\n"
                        + "          semaphore 'wait'\n"
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "  post {\n"
                        + "    always {\n"
                        + "      echo 'Hi there'\n"
                        + "    }\n"
                        + "  }\n"
                        + "}",
                    true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
          }
        });
    rr.addStep(
        new Statement() {
          @Override
          public void evaluate() throws Throwable {
            WorkflowJob p = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class);
            assertNotNull(p);
            WorkflowRun b = p.getBuildByNumber(1);
            SemaphoreStep.success("wait/1", null);
            rr.j.assertBuildStatusSuccess(rr.j.waitForCompletion(b));
            rr.j.assertLogContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")", b);
            FlowExecution execution = b.getExecution();
            assertNotNull(execution);
            Collection<FlowNode> heads = execution.getCurrentHeads();

            DepthFirstScanner scanner = new DepthFirstScanner();

            assertNotNull(
                scanner.findFirstMatch(
                    heads,
                    null,
                    syntheticStagePredicate(
                        SyntheticStageNames.postBuild(), SyntheticStage.getPost())));
            SemaphoreStep.success("wait/2", null);
            rr.j.buildAndAssertSuccess(p);
          }
        });
  }
}
