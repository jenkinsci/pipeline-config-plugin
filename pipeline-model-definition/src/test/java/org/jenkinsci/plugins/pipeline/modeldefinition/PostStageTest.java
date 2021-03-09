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

import hudson.model.Result;
import hudson.model.Slave;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/** Tests for {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage#post} */
public class PostStageTest extends AbstractModelDefTest {

  private static Slave s;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setLabelString("here");
  }

  @Test
  public void globalAndLocalAlways() throws Exception {
    expect("globalAndLocalAlways").logContains("Post stage", "Local Always", "Global Always").go();
  }

  @Test
  public void localAlways() throws Exception {
    expect("localAlways").logContains("Post stage", "Local Always").go();
  }

  public static final String[] ALL_LOCAL_ALWAYS = {
    "Post stage", "hello", "And AAAAIIIAAAIAI", "I AM ALWAYS WITH YOU"
  };

  @Test
  public void withAllLocalUnstable() throws Exception {
    env(s).put("MAKE_RESULT", Result.UNSTABLE.toString()).set();
    expect(Result.UNSTABLE, "localAll")
        .logContains(ALL_LOCAL_ALWAYS)
        .logContains("Setting build result UNSTABLE", "I AM UNSTABLE", "I HAVE CHANGED")
        .logNotContains("I WAS ABORTED", "I FAILED", "MOST DEFINITELY FINISHED")
        .go();
  }

  @Test
  public void withAllLocalUnsuccessfulWithUnstable() throws Exception {
    env(s).put("MAKE_RESULT", Result.UNSTABLE.toString()).set();
    expect(Result.UNSTABLE, "unsuccessful")
        .logContains("I LOVE YOU VIRGINIA")
        .logContains("I FAILED YOU, SORRY")
        .logContains("I AM UNSTABLE")
        .go();
  }

  @Test
  public void withAllLocalUnsuccessfulWithAborted() throws Exception {
    env(s).put("MAKE_RESULT", Result.ABORTED.toString()).set();
    expect(Result.ABORTED, "unsuccessful")
        .logContains("I LOVE YOU VIRGINIA")
        .logContains("I FAILED YOU, SORRY")
        .go();
  }

  @Test
  public void withAllLocalUnsuccessfulWithFailure() throws Exception {
    env(s).put("MAKE_RESULT", Result.FAILURE.toString()).set();
    expect(Result.FAILURE, "unsuccessful")
        .logContains("I LOVE YOU VIRGINIA")
        .logContains("I FAILED YOU, SORRY")
        .go();
  }

  @Issue("JENKINS-55476")
  @Test
  public void withAllLocalUnsuccessfulWithSuccess() throws Exception {
    env(s).put("MAKE_RESULT", Result.SUCCESS.toString()).set();
    expect(Result.SUCCESS, "unsuccessful")
        .logContains("I LOVE YOU VIRGINIA")
        .logNotContains("I FAILED YOU, SORRY")
        .go();
  }

  @Test
  public void withAllLocalUnsuccessfulWithNotBuilt() throws Exception {
    env(s).put("MAKE_RESULT", Result.NOT_BUILT.toString()).set();
    expect(Result.NOT_BUILT, "unsuccessful")
        .logContains("I LOVE YOU VIRGINIA")
        .logContains("I FAILED YOU, SORRY")
        .go();
  }

  @Test
  public void withAllLocalFailure() throws Exception {
    env(s).put("MAKE_RESULT", Result.FAILURE.toString()).set();
    expect(Result.FAILURE, "localAll")
        .logContains(ALL_LOCAL_ALWAYS)
        .logContains("Setting build result FAILURE", "I FAILED", "I HAVE CHANGED")
        .logNotContains("I WAS ABORTED", "I AM UNSTABLE", "MOST DEFINITELY FINISHED")
        .go();
  }

  @Test
  public void withAllLocalAborted() throws Exception {
    env(s).put("MAKE_RESULT", Result.ABORTED.toString()).set();
    expect(Result.ABORTED, "localAll")
        .logContains(ALL_LOCAL_ALWAYS)
        .logContains("Setting build result ABORTED", "I WAS ABORTED", "I HAVE CHANGED")
        .logNotContains("I FAILED", "I AM UNSTABLE", "MOST DEFINITELY FINISHED")
        .go();
  }

  @Test
  public void withAllLocalSuccess() throws Exception {
    env(s).set();
    expect(Result.SUCCESS, "localAll")
        .logContains(ALL_LOCAL_ALWAYS)
        .logContains("All is well", "MOST DEFINITELY FINISHED", "I HAVE CHANGED")
        .logNotContains("I WAS ABORTED", "I FAILED", "I AM UNSTABLE")
        .go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void withGroupAllLocalSuccess() throws Exception {
    env(s).set();
    expect(Result.SUCCESS, "groupLocalAll")
        .logContains(ALL_LOCAL_ALWAYS)
        .logContains("All is well", "MOST DEFINITELY FINISHED", "I HAVE CHANGED")
        .logNotContains("I WAS ABORTED", "I FAILED", "I AM UNSTABLE")
        .go();
  }

  @Test
  public void withAllLocalChanged() throws Exception {
    env(s).set();
    ExpectationsBuilder expect = expect(Result.SUCCESS, "localAll").logContains(ALL_LOCAL_ALWAYS);
    expect
        .logContains("All is well", "MOST DEFINITELY FINISHED", "I HAVE CHANGED")
        .logNotContains("I WAS ABORTED", "I FAILED", "I AM UNSTABLE")
        .go();
    expect
        .resetForNewRun(Result.SUCCESS)
        .logContains("All is well", "MOST DEFINITELY FINISHED")
        .logNotContains("I WAS ABORTED", "I FAILED", "I AM UNSTABLE", "I HAVE CHANGED")
        .go();

    env(s).put("MAKE_RESULT", Result.UNSTABLE.toString()).set();
    expect
        .resetForNewRun(Result.UNSTABLE)
        .logContains(ALL_LOCAL_ALWAYS)
        .logContains("Setting build result UNSTABLE", "I AM UNSTABLE", "I HAVE CHANGED")
        .logNotContains("I WAS ABORTED", "I FAILED", "MOST DEFINITELY FINISHED")
        .go();
  }

  @Issue("JENKINS-46276")
  @Test
  public void withAgentNoneAndAgentAny() throws Exception {
    expect("withAgentNoneAndAgentAny")
        .logNotContains("Required context class hudson.FilePath is missing")
        .go();
  }

  @Issue("JENKINS-47928")
  @Test
  public void parallelParentPostFailure() throws Exception {
    expect(Result.FAILURE, "parallelParentPostFailure").logNotContains("PARALLEL STAGE POST").go();
  }

  @Test
  public void postWithOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    expect("postWithOutsideVarAndFunc")
        .logContains("Hi there - This comes from a function")
        .logNotContains("I FAILED")
        .go();
  }

  @Issue("JENKINS-48266")
  @Test
  public void postAfterParallel() throws Exception {
    expect("postAfterParallel").logContains("Post ran").go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void postInParallelAndSequential() throws Exception {
    expect("postInParallelAndSequential")
        .logContains("Post Nested 1 ran", "Post Nested 2 ran", "Post Child 2 ran", "Post ran")
        .go();
  }

  @Issue("JENKINS-52114")
  @Test
  public void postFailureSuccessInParallel() throws Exception {
    expect(Result.FAILURE, "postFailureSuccessInParallel")
        .logContains(
            "Post Nested 1 ran",
            "Post Nested 2 ran",
            "Post Child 2 ran",
            "Post ran",
            "Found failure in Child 1",
            "Found success in Nested 2",
            "Found success in Child 2",
            "Found failure in Child 3",
            "Parallel parent failure")
        .logNotContains(
            "Found success in Child 1",
            "Found failure in Nested 2",
            "Found failure in Child 2",
            "Found success in Child 3",
            "Parallel parent success")
        .go();
  }

  @Issue("JENKINS-52114")
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
                + "            post {\n"
                + "                success {\n"
                + "                    echo 'I AM SUCCESSFUL'\n"
                + "                }\n"
                + "                aborted {\n"
                + "                    echo 'I AM ABORTED'\n"
                + "                }\n"
                + "                failure {\n"
                + "                    echo 'I FAILED'\n"
                + "                }\n"
                + "            }\n"
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

    j.assertLogNotContains("I AM SUCCESSFUL", run1);

    j.assertLogNotContains("I FAILED", run1);
  }

  @Issue("JENKINS-57826")
  @Test
  public void catchErrorStageFailure() throws Exception {
    expect("catchErrorStageFailure")
        .logContains("This should happen", "The build should be a success")
        .logNotContains("This shouldn't happen", "The build shouldn't be a failure")
        .go();
  }

  @Issue("JENKINS-57826")
  @Test
  public void catchErrorStageFailureNested() throws Exception {
    expect("catchErrorStageFailureNested")
        .logContains("This should happen", "The build should be a success")
        .logNotContains("This shouldn't happen", "The build shouldn't be a failure")
        .go();
  }

  @Issue("JENKINS-57826")
  @Test
  public void catchErrorStageAborted() throws Exception {
    expect("catchErrorStageAborted")
        .logContains("This should happen", "The build should be a success")
        .logNotContains("This shouldn't happen", "The build shouldn't be aborted")
        .go();
  }

  @Issue("JENKINS-57826")
  @Test
  public void catchErrorStageUnstable() throws Exception {
    expect("catchErrorStageUnstable")
        .logContains("This should happen", "The build should be a success")
        .logNotContains("This shouldn't happen", "The build shouldn't be unstable")
        .go();
  }

  @Issue("JENKINS-57826")
  @Test
  public void catchErrorStageUnstableBuildFailure() throws Exception {
    expect(Result.FAILURE, "catchErrorStageUnstableBuildFailure")
        .logContains("This should happen", "The build should be a failure")
        .logNotContains("This shouldn't happen", "The build shouldn't be unstable")
        .go();
  }

  @Issue("JENKINS-57826")
  @Test
  public void catchErrorStageNotBuilt() throws Exception {
    expect("catchErrorStageNotBuilt")
        .logContains("This should happen", "The build should be a success")
        .logNotContains("This shouldn't happen", "The build shouldn't be not built")
        .go();
  }

  @Issue("JENKINS-57826")
  @Test
  public void warnErrorStageUnstable() throws Exception {
    expect(Result.UNSTABLE, "warnErrorStageUnstable")
        .logContains("This should happen", "The build should be unstable")
        .logNotContains("This shouldn't happen", "The build shouldn't be a success")
        .go();
  }

  @Override
  protected ExpectationsBuilder expect(String resource) {
    return super.expect("postStage", resource);
  }

  @Override
  protected ExpectationsBuilder expect(Result result, String resource) {
    return super.expect(result, "postStage", resource);
  }
}
