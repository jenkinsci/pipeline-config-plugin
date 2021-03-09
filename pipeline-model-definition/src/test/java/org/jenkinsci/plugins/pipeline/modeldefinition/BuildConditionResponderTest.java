/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

/** @author Andrew Bayer */
public class BuildConditionResponderTest extends AbstractModelDefTest {

  @Test
  public void simplePostBuild() throws Exception {
    expect("postStage/simplePostBuild")
        .logContains(
            "[Pipeline] { (foo)",
            "hello",
            "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
            "I HAVE FINISHED",
            "MOST DEFINITELY FINISHED")
        .logNotContains("I FAILED")
        .go();
  }

  @Test
  public void postChecksAllConditions() throws Exception {
    expect(Result.FAILURE, "postStage/postChecksAllConditions")
        .logContains(
            "[Pipeline] { (foo)",
            "hello",
            "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
            "I AM FAILING NOW",
            "I FAILED",
            "I RAN ANYWAY")
        .logNotContains("MOST DEFINITELY FINISHED")
        .go();
  }

  @Issue("JENKINS-50645")
  @Test
  public void postFailureAfterSuccess() throws Exception {
    // NOTE: Not checking log in order because "I AM FAILING NOW" doesn't actually show up until the
    // end of the Pipeline.
    // That's expected/normal behavior for the error step.
    expect(Result.FAILURE, "postStage/postFailureAfterSuccess")
        .logContains("I AM FAILING NOW", "I FAILED")
        .go();
  }

  @Test
  public void postFailureAfterManualResultChange() throws Exception {
    expect(Result.FAILURE, "postStage/postFailureAfterManualResultChange")
        .logContains("MANUALLY CHANGED RESULT TO FAILURE", "FAILURE CONDITION RAN")
        .go();
  }

  @Issue("JENKINS-50645")
  @Test
  public void postFailureAfterUnstable() throws Exception {
    // NOTE: Not checking log in order because "I AM FAILING NOW" doesn't actually show up until the
    // end of the Pipeline.
    // That's expected/normal behavior for the error step.
    expect(Result.FAILURE, "postStage/postFailureAfterUnstable")
        .logContains("I AM FAILING NOW", "I FAILED")
        .go();
  }

  @Test
  public void postOnChanged() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("postStage/postOnChangeFailed");
    j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
    j.assertLogContains("[Pipeline] { (foo)", b);
    j.assertLogContains("I FAILED", b);
    j.assertLogNotContains("I REGRESSED", b);

    WorkflowJob job = b.getParent();
    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("postStage/postOnChangeChanged"), true));
    WorkflowRun b2 = j.buildAndAssertSuccess(job);
    j.assertLogContains("[Pipeline] { (foo)", b2);
    j.assertLogContains("hello", b2);
    j.assertLogContains("I CHANGED", b2);
    j.assertLogContains("I AM FIXED", b2);

    // Now make sure we don't get any alert this time.
    WorkflowRun b3 = j.buildAndAssertSuccess(job);
    j.assertLogContains("[Pipeline] { (foo)", b3);
    j.assertLogContains("hello", b3);
    j.assertLogNotContains("I CHANGED", b3);
    j.assertLogNotContains("I FAILED", b3);
    j.assertLogNotContains("I AM FIXED", b3);

    // And one more time for regression
    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("postStage/postOnChangeFailed"), true));
    WorkflowRun b4 = job.scheduleBuild2(0).waitForStart();
    j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b4));
    j.assertLogContains("[Pipeline] { (foo)", b4);
    j.assertLogContains("I FAILED", b4);
    j.assertLogContains("I REGRESSED", b4);
  }

  @Test
  public void unstablePost() throws Exception {
    expect(Result.UNSTABLE, "postStage/unstablePost")
        .logContains("[Pipeline] { (foo)", "hello", "I AM UNSTABLE")
        .logNotContains("I FAILED")
        .go();
  }

  @Issue("JENKINS-38993")
  @Test
  public void buildConditionOrdering() throws Exception {
    expect("postStage/buildConditionOrdering")
        .logContains("[Pipeline] { (foo)", "hello")
        .logContainsInOrder("I AM ALWAYS", "I CHANGED", "I SUCCEEDED")
        .go();
  }

  @Issue("JENKINS-43339")
  @Test
  public void notBuiltFlowInterruptedException() throws Exception {
    WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "milestone-flow-interrupted");
    job.setDefinition(
        new CpsFlowDefinition(
            ""
                + "pipeline {\n"
                + "  agent none\n"
                + "  stages {\n"
                + "    stage('milestones') {\n"
                + "      steps {\n"
                + "        milestone(1)\n"
                + "        semaphore 'wait'\n"
                + "        milestone(2)\n"
                + "      }\n"
                + "      post {\n"
                + "        notBuilt {\n"
                + "          echo 'Job not built due to milestone'\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n",
            true));

    WorkflowRun run1 = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("wait/1", run1);
    WorkflowRun run2 = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("wait/2", run2);

    SemaphoreStep.success("wait/2", null);

    j.assertBuildStatusSuccess(j.waitForCompletion(run2));

    j.assertBuildStatus(Result.NOT_BUILT, j.waitForCompletion(run1));

    j.assertLogContains("Job not built due to milestone", run1);

    j.assertLogNotContains("Job not built due to milestone", run2);
  }

  @Issue("JENKINS-43339")
  @Test
  public void abortedFlowInterruptedException() throws Exception {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    j.jenkins.setAuthorizationStrategy(
        new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("ops"));
    WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "input-flow-interrupted");
    job.setDefinition(
        new CpsFlowDefinition(
            ""
                + "pipeline {\n"
                + "  agent none\n"
                + "  stages {\n"
                + "    stage('input') {\n"
                + "      steps {\n"
                + "        input(id: 'InputX', message: 'OK?', ok: 'Yes', submitter: 'ops')\n"
                + "      }\n"
                + "      post {\n"
                + "        aborted {\n"
                + "          echo 'Job aborted due to input'\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n",
            true));

    QueueTaskFuture<WorkflowRun> queueTaskFuture = job.scheduleBuild2(0);
    assertNotNull(queueTaskFuture);
    WorkflowRun run = queueTaskFuture.getStartCondition().get();
    CpsFlowExecution execution = (CpsFlowExecution) run.getExecutionPromise().get();

    while (run.getAction(InputAction.class) == null) {
      execution.waitForSuspension();
    }

    JenkinsRule.WebClient webClient = j.createWebClient();

    webClient.login("ops");

    InputAction inputAction = run.getAction(InputAction.class);
    InputStepExecution is = inputAction.getExecution("InputX");
    HtmlPage p = webClient.getPage(run, inputAction.getUrlName());

    j.submit(p.getFormByName(is.getId()), "abort");
    assertEquals(0, inputAction.getExecutions().size());
    queueTaskFuture.get();

    j.assertBuildStatus(Result.ABORTED, j.waitForCompletion(run));

    j.assertLogContains("Job aborted due to input", run);
  }

  @Issue("JENKINS-37663")
  @Test
  public void contextResultOverridesRunResult() throws Exception {
    expect(Result.UNSTABLE, "contextResultOverridesRunResult")
        .otherResource("junitResult.xml", "junitResult.xml")
        .logContains("I AM UNSTABLE")
        .logNotContains("MOST DEFINITELY FINISHED")
        .go();
  }

  @Issue("JENKINS-48752")
  @Test
  public void changedAndNotSuccess() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("postStage/postOnChangeFailed");
    j.assertBuildStatus(Result.FAILURE, j.waitForCompletion(b));
    j.assertLogContains("[Pipeline] { (foo)", b);
    j.assertLogContains("I FAILED", b);

    WorkflowJob job = b.getParent();
    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("postStage/postOnChangeUnstable"), true));
    WorkflowRun b2 =
        j.assertBuildStatus(Result.UNSTABLE, j.waitForCompletion(job.scheduleBuild2(0).get()));
    j.assertLogContains("[Pipeline] { (foo)", b2);
    j.assertLogContains("hello", b2);
    j.assertLogContains("I CHANGED", b2);

    // Now make sure we don't get any alert this time.
    WorkflowRun b3 =
        j.assertBuildStatus(Result.UNSTABLE, j.waitForCompletion(job.scheduleBuild2(0).get()));
    j.assertLogContains("[Pipeline] { (foo)", b3);
    j.assertLogContains("hello", b3);
    j.assertLogNotContains("I CHANGED", b3);
    j.assertLogNotContains("I FAILED", b3);
  }

  @Issue("JENKINS-50652")
  @Test
  public void abortedShouldNotTriggerFailure() throws Exception {
    assumeSh();
    WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "abort");
    job.setDefinition(
        new CpsFlowDefinition(
            ""
                + "pipeline {\n"
                + "    agent any\n"
                + "    stages {\n"
                + "        stage('foo') {\n"
                + "            steps {\n"
                + "                echo 'hello'\n"
                + "                semaphore 'wait-again'\n"
                + "                sh 'sleep 15'\n"
                + "            }\n"
                + "        }\n"
                + "    }\n"
                + "    post {\n"
                + "        aborted {\n"
                + "            echo 'I AM ABORTED'\n"
                + "        }\n"
                + "        failure {\n"
                + "            echo 'I FAILED'\n"
                + "        }\n"
                + "    }\n"
                + "}\n",
            true));

    WorkflowRun run1 = job.scheduleBuild2(0).waitForStart();
    SemaphoreStep.waitForStart("wait-again/1", run1);
    SemaphoreStep.success("wait-again/1", null);
    Thread.sleep(1000);
    run1.doStop();

    j.waitForCompletion(run1);

    j.assertLogContains("I AM ABORTED", run1);

    j.assertLogNotContains("I FAILED", run1);
  }

  @Issue("JENKINS-44993")
  @Test
  public void failureInPostBlock() throws Exception {
    expect(Result.FAILURE, "postStage/failureInPostBlock")
        .logContains(
            "[Pipeline] { (foo)",
            "hello",
            "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
            "I FAILED",
            "CLEANUP RAN",
            "Error when executing failure post condition",
            "Error when executing always post condition",
            "No such property: otherUndefined for class",
            "No such property: undefined for class")
        .logNotContains("MOST DEFINITELY FINISHED")
        .go();
  }

  @Issue("JENKINS-52084")
  @Test
  public void sequentialPostNode() throws Exception {
    expect("postStage/sequentialPostNode").go();
  }

  @Issue("JENKINS-51383")
  @Test
  public void postSuccessNoSuchMethodError() throws Exception {
    expect(Result.FAILURE, "postStage/postSuccessNoSuchMethodError")
        .logContains("[Pipeline] { (foo)", "before missing")
        .logNotContains("MOST DEFINITELY FINISHED", "after missing")
        .go();
  }

  @Issue("JENKINS-56402")
  @Test
  public void resultIsFailureInPostAfterStageFailure() throws Exception {
    expect(Result.FAILURE, "postStage/resultIsFailureInPostAfterStageFailure")
        .logContains(
            "Result in always: FAILURE",
            "CurrentResult in always: FAILURE",
            "Result in failure: FAILURE",
            "CurrentResult in failure: FAILURE")
        .go();
  }

  @Issue("JENKINS-56402")
  @Test
  public void resultIsFailureInPostAfterPostFailure() throws Exception {
    expect(Result.FAILURE, "postStage/resultIsFailureInPostAfterPostFailure")
        .logContains(
            "Result in always: SUCCESS",
            "CurrentResult in always: SUCCESS",
            "Result in failure: FAILURE",
            "CurrentResult in failure: FAILURE",
            "Result in cleanup: FAILURE",
            "CurrentResult in cleanup: FAILURE")
        .go();
  }

  @Issue("JENKINS-56402")
  @Test
  public void resultIsFailureInPostAfterResultModified() throws Exception {
    expect(Result.FAILURE, "postStage/resultIsFailureInPostAfterResultModified")
        .logContains(
            "Result in always: UNSTABLE",
            "CurrentResult in always: UNSTABLE",
            "Result in cleanup: FAILURE",
            "CurrentResult in cleanup: FAILURE")
        .go();
  }

  @Issue("JENKINS-56402")
  @Test
  public void resultStaysFailureWhenManuallySetInPost() throws Exception {
    expect(Result.FAILURE, "postStage/resultStaysFailureWhenManuallySetInPost")
        .logContains(
            "Result in always: FAILURE",
            "CurrentResult in always: FAILURE",
            "Result in cleanup: FAILURE",
            "CurrentResult in cleanup: FAILURE")
        .go();
  }

  @Issue("JENKINS-56402")
  @Test
  public void resultIsSuccessInPost() throws Exception {
    expect(Result.SUCCESS, "postStage/resultIsSuccessInPost")
        .logContains(
            "Result in always: SUCCESS",
            "CurrentResult in always: SUCCESS",
            "Result in success: SUCCESS",
            "CurrentResult in success: SUCCESS")
        .go();
  }
}
