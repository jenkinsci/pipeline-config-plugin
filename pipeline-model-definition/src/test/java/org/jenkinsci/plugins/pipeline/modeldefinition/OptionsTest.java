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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.BaseCredentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.LogRotator;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.util.Secret;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.branch.RateLimitBranchProperty;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class OptionsTest extends AbstractModelDefTest {
  @Test
  public void simpleJobProperties() throws Exception {
    WorkflowRun b =
        expect("options/simpleJobProperties")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
            .go();

    WorkflowJob p = b.getParent();

    BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
    assertNotNull(bdp);
    BuildDiscarder strategy = bdp.getStrategy();
    assertNotNull(strategy);
    assertEquals(LogRotator.class, strategy.getClass());
    LogRotator lr = (LogRotator) strategy;
    assertEquals(1, lr.getNumToKeep());
  }

  @Ignore("Properties are set before withEnv is called.")
  @Test
  public void envVarInOptions() throws Exception {
    WorkflowRun b =
        expect("environment/envVarInOptions")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
            .go();

    WorkflowJob p = b.getParent();

    BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
    assertNotNull(bdp);
    BuildDiscarder strategy = bdp.getStrategy();
    assertNotNull(strategy);
    assertEquals(LogRotator.class, strategy.getClass());
    LogRotator lr = (LogRotator) strategy;
    assertEquals(1, lr.getNumToKeep());
  }

  @Test
  public void multipleProperties() throws Exception {
    WorkflowRun b =
        expect(Result.FAILURE, "multipleProperties")
            .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "hello")
            .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
            .go();

    WorkflowJob p = b.getParent();

    // We test for skipDefaultCheckout() in the Jenkinsfile itself by verifying that Jenkinsfile
    // isn't in the workspace

    // Job properties
    BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
    assertNotNull(bdp);
    BuildDiscarder strategy = bdp.getStrategy();
    assertNotNull(strategy);
    assertEquals(LogRotator.class, strategy.getClass());
    LogRotator lr = (LogRotator) strategy;
    assertEquals(1, lr.getNumToKeep());

    DisableConcurrentBuildsJobProperty dcbjp =
        p.getProperty(DisableConcurrentBuildsJobProperty.class);
    assertNotNull(dcbjp);

    // Parameters
    ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(pdp);

    assertEquals(2, pdp.getParameterDefinitions().size());
    BooleanParameterDefinition bpd =
        getParameterOfType(pdp.getParameterDefinitions(), BooleanParameterDefinition.class);
    assertNotNull(bpd);
    assertEquals("flag", bpd.getName());
    assertTrue(bpd.isDefaultValue());

    StringParameterDefinition spd =
        getParameterOfType(pdp.getParameterDefinitions(), StringParameterDefinition.class);
    assertNotNull(spd);
    assertEquals("SOME_STRING", spd.getName());

    // Trigger(s)
    PipelineTriggersJobProperty trigProp = p.getProperty(PipelineTriggersJobProperty.class);
    assertNotNull(trigProp);

    assertEquals(1, trigProp.getTriggers().size());
    TimerTrigger.DescriptorImpl timerDesc =
        j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

    Trigger trigger = trigProp.getTriggerForDescriptor(timerDesc);
    assertNotNull(trigger);

    assertTrue(trigger instanceof TimerTrigger);
    TimerTrigger timer = (TimerTrigger) trigger;
    assertEquals("@daily", timer.getSpec());
  }

  @Test
  public void skipCheckoutFalse() throws Exception {
    expect("options/skipCheckoutFalse").logContains("[Pipeline] { (foo)", "hello").go();
  }

  @Issue("JENKINS-44277")
  @Test
  public void checkoutToSubdirectory() throws Exception {
    expect("options/checkoutToSubdirectory").logContains("[Pipeline] { (foo)", "hello").go();
  }

  @Issue("JENKINS-44277")
  @Test
  public void checkoutToSubdirectoryWithOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    expect("options/checkoutToSubdirectoryWithOutsideVarAndFunc")
        .logContains("[Pipeline] { (foo)", "hello")
        .go();
  }

  @Test
  public void simpleWrapper() throws Exception {
    expect("options/simpleWrapper")
        .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "hello")
        .logNotContains("[Pipeline] { (Post Actions)")
        .go();
  }

  @Ignore(
      "Technically we could allow env vars in wrappers, since wrappers get invoked within env block, but since "
          + "we can't for properties, triggers, or parameters due to being invoked before the env block, let's be "
          + "consistent")
  @Test
  public void envVarInWrapper() throws Exception {
    expect("environment/envVarInWrapper")
        .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "hello")
        .logNotContains("[Pipeline] { (Post Actions)")
        .go();
  }

  @Test
  public void multipleWrappers() throws Exception {
    expect("multipleWrappers")
        .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "[Pipeline] retry", "hello")
        .logNotContains("[Pipeline] { (Post Actions)")
        .go();
  }

  @Issue("JENKINS-44149")
  @Test
  public void propsRemoved() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("options/simpleJobProperties");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    assertNotNull(job.getProperty(BuildDiscarderProperty.class));

    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    assertNull(job.getProperty(BuildDiscarderProperty.class));
  }

  @Issue("JENKINS-44621")
  @Test
  public void externalPropsNotRemoved() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("options/simpleJobProperties");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    assertNotNull(job.getProperty(BuildDiscarderProperty.class));
    job.addProperty(new DisableConcurrentBuildsJobProperty());
    job.setQuietPeriod(15);
    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    assertNull(job.getProperty(BuildDiscarderProperty.class));

    assertNotNull(job.getProperty(DisableConcurrentBuildsJobProperty.class));

    int externalPropCount = 0;
    for (JobProperty p : job.getAllProperties()) {
      if (p instanceof DisableConcurrentBuildsJobProperty) {
        externalPropCount++;
      }
    }

    assertEquals(1, externalPropCount);
    assertEquals(15, job.getQuietPeriod());
  }

  @Issue("JENKINS-44809")
  @Test
  public void duplicateExternalPropsCleaned() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("options/simpleJobProperties"), true));
    j.buildAndAssertSuccess(job);

    assertNotNull(job.getProperty(BuildDiscarderProperty.class));
    assertNull(job.getProperty(ParametersDefinitionProperty.class));
    DeclarativeJobPropertyTrackerAction action2 =
        job.getAction(DeclarativeJobPropertyTrackerAction.class);
    assertNotNull(action2);
    assertTrue(action2.getParameters().isEmpty());

    job.addProperty(new DisableConcurrentBuildsJobProperty());
    job.addProperty(new DisableConcurrentBuildsJobProperty());
    job.addProperty(new DisableConcurrentBuildsJobProperty());

    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    assertNull(job.getProperty(BuildDiscarderProperty.class));
    assertNull(job.getProperty(ParametersDefinitionProperty.class));

    assertNotNull(job.getProperty(DisableConcurrentBuildsJobProperty.class));

    DeclarativeJobPropertyTrackerAction action3 =
        job.getAction(DeclarativeJobPropertyTrackerAction.class);
    assertNotNull(action3);
    assertTrue(action3.getParameters().isEmpty());

    int externalPropCount = 0;
    for (JobProperty p : job.getAllProperties()) {
      if (p instanceof DisableConcurrentBuildsJobProperty) {
        externalPropCount++;
      }
    }

    assertEquals(1, externalPropCount);
  }

  @Issue("JENKINS-46403")
  @Test
  public void replaceLogRotatorWithBuildDiscarderProperty() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("options/simpleJobProperties"), true));

    // we want to test a job with an old-style logRotator property, for which there is no setter
    // anymore
    try {
      Field deprecatedLogRotatorField = Job.class.getDeclaredField("logRotator");
      deprecatedLogRotatorField.setAccessible(true);
      deprecatedLogRotatorField.set(job, new LogRotator(-1, 42, -1, 2));
      job.save();
      assertNotNull(job.getBuildDiscarder());
      if (job.getBuildDiscarder() instanceof LogRotator) {
        LogRotator lr = (LogRotator) job.getBuildDiscarder();
        assertEquals(lr.getNumToKeep(), 42);
      }
    } catch (NoSuchFieldException e) {
      // if there is no such field anymore, then I guess there is no potential issue anymore
    }

    // run the job
    QueueTaskFuture<WorkflowRun> f = job.scheduleBuild2(0);
    assertNotNull(f);
    // wait up to 10 seconds, fail test on timeout (see livelock described in JENKINS-46403)
    try {
      j.waitUntilNoActivityUpTo(10_000);
      // we could use f.get(...) and TimeoutException instead, but this is convenient to dump
      // threads
    } catch (AssertionError ae) {
      // makes termination faster by actually killing the job (avoids another 60 seconds)
      f.getStartCondition().get(100L, TimeUnit.MILLISECONDS).doKill();
      throw ae;
    }

    // no timeout, the build should be blue
    j.assertBuildStatusSuccess(f);

    // check the original old-style LogRotator has been replaced by something else
    assertNotNull(job.getBuildDiscarder());
    if (job.getBuildDiscarder() instanceof LogRotator) {
      LogRotator lr = (LogRotator) job.getBuildDiscarder();
      assertNotEquals(lr.getNumToKeep(), 42);
    }
  }

  @Issue("TBD")
  @Test
  public void retryOptions() throws Exception {
    expect(Result.FAILURE, "options/retryOptions").logContains("Retrying").go();
  }

  @Ignore(
      "jira-step pulls in dependencies that break form submission, so...ignore this test. We know it's fine now anyway.")
  @Issue("JENKINS-48115")
  @Test
  public void disableConcurrentBuilds() throws Exception {
    WorkflowRun b = expect("options/disableConcurrentBuilds").go();
    WorkflowJob p = b.getParent();

    DisableConcurrentBuildsJobProperty prop =
        p.getProperty(DisableConcurrentBuildsJobProperty.class);
    assertNotNull(prop);
  }

  @Issue("JENKINS-48380")
  @Test
  public void stageWrapper() throws Exception {
    expect("stageWrapper")
        .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "hello")
        .logNotContains("[Pipeline] { (Post Actions)")
        .go();
  }

  @Issue("JENKINS-48380")
  @Test
  public void skipCheckoutInStage() throws Exception {
    expect("options/skipCheckoutInStage").logContains("[Pipeline] { (foo)", "hello").go();
  }

  @Issue("JENKINS-51227")
  @Test
  public void quietPeriod() throws Exception {
    WorkflowRun b = expect("options/quietPeriod").logContains("hello").go();

    WorkflowJob p = b.getParent();
    assertNotNull(p);
    assertEquals(15, p.getQuietPeriod());
  }

  @Issue("JENKINS-51227")
  @Test
  public void quietPeriodRemoved() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("options/quietPeriod");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    assertEquals(15, job.getQuietPeriod());

    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    assertEquals(j.jenkins.getQuietPeriod(), job.getQuietPeriod());
  }

  @Issue("JENKINS-48380")
  @Test
  public void withCredentialsWrapper() throws Exception {
    final String credentialsId = "creds";
    final String username = "bob";
    final String passphrase = "s3cr3t";
    final String keyContent = "the-key";
    SSHUserPrivateKey c = new DummyPrivateKey(credentialsId, username, passphrase, keyContent);
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);

    expect("options/withCredentialsWrapper")
        .archives("userPass.txt", username + ":" + passphrase)
        .archives("key.txt", keyContent)
        .go();
  }

  @Issue("JENKINS-48380")
  @Test
  public void withCredentialsStageWrapper() throws Exception {
    final String credentialsId = "creds";
    final String username = "bob";
    final String passphrase = "s3cr3t";
    final String keyContent = "the-key";
    SSHUserPrivateKey c = new DummyPrivateKey(credentialsId, username, passphrase, keyContent);
    CredentialsProvider.lookupStores(j.jenkins)
        .iterator()
        .next()
        .addCredentials(Domain.global(), c);

    expect("options/withCredentialsStageWrapper")
        .logContains("THEUSER is null")
        .archives("userPass.txt", username + ":" + passphrase)
        .archives("key.txt", keyContent)
        .go();
  }

  @Issue("JENKINS-50561")
  @Test
  public void rateLimitBuilds() throws Exception {
    WorkflowRun b = expect("options/rateLimitBuilds").go();
    WorkflowJob p = b.getParent();

    RateLimitBranchProperty.JobPropertyImpl prop =
        p.getProperty(RateLimitBranchProperty.JobPropertyImpl.class);
    assertNotNull(prop);
    assertEquals(1, prop.getCount());
    assertEquals("day", prop.getDurationName());
    assertFalse(prop.isUserBoost());

    QueueTaskFuture<WorkflowRun> inQueue = p.scheduleBuild2(0);

    while (!Queue.getInstance().contains(p)) {
      Thread.yield();
    }

    Queue.getInstance().maintain();
    Queue.Item queued = Queue.getInstance().getItem(p);
    assertThat(queued.isBlocked(), is(true));
    assertThat(
        queued.getCauseOfBlockage().getShortDescription().toLowerCase(),
        containsString("throttle"));

    inQueue.cancel(true);
  }

  @Issue("JENKINS-46354")
  @Test
  public void topLevelRetryExecutesAllStages() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    expect("options/topLevelRetryExecutesAllStages")
        .logContains("Actually executing stage Bar")
        .go();
  }

  @Issue("JENKINS-46354")
  @Test
  public void parentStageRetryExecutesAllChildStages() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    expect("options/parentStageRetryExecutesAllChildStages")
        .logContains("Actually executing stage Bar", "Actually executing stage Baz")
        .go();
  }

  @Issue("JENKINS-42039")
  @Test
  public void skipAfterUnstableWithOption() throws Exception {
    WorkflowRun b =
        expect(Result.UNSTABLE, "options/skipAfterUnstableIfOption")
            .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)")
            .logNotContains("goodbye")
            .go();

    FlowExecution execution = b.getExecution();
    assertNotNull(execution);
    List<FlowNode> heads = execution.getCurrentHeads();
    DepthFirstScanner scanner = new DepthFirstScanner();
    FlowNode startFoo =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("foo"));
    assertNotNull(startFoo);
    assertTrue(startFoo instanceof StepStartNode);
    FlowNode endFoo =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((StepStartNode) startFoo));
    assertNotNull(endFoo);
    assertEquals(
        GenericStatus.UNSTABLE,
        StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
  }

  @Issue("JENKINS-42039")
  @Test
  public void dontSkipAfterUnstableByDefault() throws Exception {
    expect(Result.UNSTABLE, "options/dontSkipAfterUnstableByDefault")
        .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)", "goodbye")
        .go();
  }

  @Test
  public void sameJobPropertiesNotOverride() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("options/simpleJobProperties");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));
    WorkflowJob job = b.getParent();

    BuildDiscarderProperty bdp = job.getProperty(BuildDiscarderProperty.class);
    assertNotNull(bdp);
    BuildDiscarder strategy = bdp.getStrategy();

    WorkflowRun b2 = job.scheduleBuild2(0).get();
    j.assertBuildStatusSuccess(j.waitForCompletion(b2));
    WorkflowJob job2 = b2.getParent();

    BuildDiscarderProperty bdp2 = job2.getProperty(BuildDiscarderProperty.class);
    assertNotNull(bdp2);
    BuildDiscarder strategy2 = bdp.getStrategy();

    assertSame(strategy, strategy2);
  }

  private static class DummyPrivateKey extends BaseCredentials
      implements SSHUserPrivateKey, Serializable {

    private final String id;
    private final String user;
    private final Secret passphrase;
    private final String keyContent;

    DummyPrivateKey(String id, String user, String passphrase, final String keyContent) {
      this.id = id;
      this.user = user;
      this.passphrase = Secret.fromString(passphrase);
      this.keyContent = keyContent;
    }

    @NonNull
    @Override
    public String getId() {
      return id;
    }

    @NonNull
    @Override
    public String getPrivateKey() {
      return keyContent;
    }

    @Override
    public Secret getPassphrase() {
      return passphrase;
    }

    @NonNull
    @Override
    public List<String> getPrivateKeys() {
      return Arrays.asList(keyContent);
    }

    @NonNull
    @Override
    public String getUsername() {
      return user;
    }

    @NonNull
    @Override
    public String getDescription() {
      return "";
    }

    @Override
    public CredentialsScope getScope() {
      return CredentialsScope.GLOBAL;
    }
  }
}
