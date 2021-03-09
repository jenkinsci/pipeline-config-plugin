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

import static org.junit.Assert.*;

import hudson.Extension;
import hudson.model.Item;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class TriggersTest extends AbstractModelDefTest {

  private static CountDownLatch triggerLatch;

  @Test
  public void simpleTriggers() throws Exception {
    WorkflowRun b =
        expect("simpleTriggers")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (Post Actions)")
            .go();

    WorkflowJob p = b.getParent();

    PipelineTriggersJobProperty triggersJobProperty = p.getTriggersJobProperty();
    assertNotNull(triggersJobProperty);
    assertEquals(1, triggersJobProperty.getTriggers().size());
    TimerTrigger.DescriptorImpl timerDesc =
        j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

    Trigger trigger = triggersJobProperty.getTriggerForDescriptor(timerDesc);
    assertNotNull(trigger);

    assertTrue(trigger instanceof TimerTrigger);
    TimerTrigger timer = (TimerTrigger) trigger;
    assertEquals("@daily", timer.getSpec());
  }

  @Test
  public void simpleTriggersWithOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    WorkflowRun b =
        expect("simpleTriggersWithOutsideVarAndFunc")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (Post Actions)")
            .go();

    WorkflowJob p = b.getParent();

    PipelineTriggersJobProperty triggersJobProperty = p.getTriggersJobProperty();
    assertNotNull(triggersJobProperty);
    assertEquals(1, triggersJobProperty.getTriggers().size());
    TimerTrigger.DescriptorImpl timerDesc =
        j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

    Trigger trigger = triggersJobProperty.getTriggerForDescriptor(timerDesc);
    assertNotNull(trigger);

    assertTrue(trigger instanceof TimerTrigger);
    TimerTrigger timer = (TimerTrigger) trigger;
    assertEquals("@daily", timer.getSpec());
  }

  @Ignore("Triggers are set before withEnv is called.")
  @Test
  public void envVarInTriggers() throws Exception {
    WorkflowRun b =
        expect("environment/envVarInTriggers")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (Post Actions)")
            .go();

    WorkflowJob p = b.getParent();

    PipelineTriggersJobProperty triggersJobProperty = p.getTriggersJobProperty();
    assertNotNull(triggersJobProperty);
    assertEquals(1, triggersJobProperty.getTriggers().size());
    TimerTrigger.DescriptorImpl timerDesc =
        j.jenkins.getDescriptorByType(TimerTrigger.DescriptorImpl.class);

    Trigger trigger = triggersJobProperty.getTriggerForDescriptor(timerDesc);
    assertNotNull(trigger);

    assertTrue(trigger instanceof TimerTrigger);
    TimerTrigger timer = (TimerTrigger) trigger;
    assertEquals("@daily", timer.getSpec());
  }

  @Issue("JENKINS-44149")
  @Test
  public void triggersRemoved() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleTriggers");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    PipelineTriggersJobProperty triggersJobProperty =
        job.getProperty(PipelineTriggersJobProperty.class);
    assertNotNull(triggersJobProperty);
    assertEquals(1, triggersJobProperty.getTriggers().size());

    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    assertNull(job.getProperty(PipelineTriggersJobProperty.class));
  }

  @Issue("JENKINS-44621")
  @Test
  public void externalTriggersNotRemoved() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleTriggers");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    PipelineTriggersJobProperty triggersJobProperty =
        job.getProperty(PipelineTriggersJobProperty.class);
    assertNotNull(triggersJobProperty);
    assertEquals(1, triggersJobProperty.getTriggers().size());

    List<Trigger> newTriggers = new ArrayList<>();
    newTriggers.addAll(triggersJobProperty.getTriggers());
    newTriggers.add(new SCMTrigger("1 1 1 * *"));
    job.removeProperty(triggersJobProperty);
    job.addProperty(new PipelineTriggersJobProperty(newTriggers));

    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    PipelineTriggersJobProperty newProp = job.getProperty(PipelineTriggersJobProperty.class);
    assertNotNull(newProp);
    assertEquals(1, newProp.getTriggers().size());
    Trigger t = newProp.getTriggers().get(0);
    assertNotNull(t);
    assertTrue(t instanceof SCMTrigger);
  }

  @Issue("JENKINS-47780")
  @Test
  public void actualTriggerCorrectScope() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleTriggers");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    expect("actualTriggerCorrectScope").go();
  }

  @LocalData
  @Test
  public void doNotRestartEqualTriggers() throws Exception {
    final int startingLatch = 100;
    // Create countdown latch to monitor how many times
    // a trigger has been restarted.
    triggerLatch = new CountDownLatch(startingLatch);

    // Create the first build. The DeclarativeJobPropertyTrackerAction action will be created.
    WorkflowRun b = getAndStartNonRepoBuild("simplePipelineWithTestTrigger");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    System.out.println("after build " + b.getId() + ": " + triggerLatch.getCount());

    // Since the tracker action was not previously available,
    // the trigger will get restarted and the latch will be decremented
    assertTrue(triggerLatch.getCount() == startingLatch - 4);

    WorkflowJob job = b.getParent();

    // Build it again.
    b = j.buildAndAssertSuccess(job);
    System.out.println("after build " + b.getId() + ": " + triggerLatch.getCount());

    // Since the trigger is the same (the config was not changed between builds),
    // it will not get restarted,
    // and the latch will NOT be decremented.
    assertTrue(triggerLatch.getCount() == startingLatch - 4);

    // Let simulate someone changing the trigger config
    PipelineTriggersJobProperty triggersJobProperty =
        job.getProperty(PipelineTriggersJobProperty.class);
    List<Trigger> newTriggers = new ArrayList<>();
    TestTrigger myTrigger3 = new TestTrigger();
    myTrigger3.setName("myTrigger3");
    newTriggers.add(myTrigger3);

    // This calls stop on the existing triggers triggers
    job.removeProperty(triggersJobProperty);

    assertTrue(triggerLatch.getCount() == startingLatch - 8);

    job.addProperty(new PipelineTriggersJobProperty(newTriggers));
    assertTrue(triggerLatch.getCount() == startingLatch - 9);

    // Build it again with a new trigger config
    b = j.buildAndAssertSuccess(job);
    System.out.println("after build " + b.getId() + ": " + triggerLatch.getCount());

    // Since the trigger is now different (the config WAS changed between builds),
    // it should get restarted,
    // and the latch WILL be decremented.
    // This stops 3 again, and also 1 and 2.
    assertTrue(triggerLatch.getCount() == startingLatch - 14);
  }

  @Test
  public void sameTriggersNotOverride() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleTriggers");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));
    WorkflowJob job = b.getParent();
    PipelineTriggersJobProperty triggersJobProperty = job.getTriggersJobProperty();
    assertNotNull(triggersJobProperty);
    Trigger t = triggersJobProperty.getTriggers().get(0);

    WorkflowRun b2 = job.scheduleBuild2(0).get();
    j.assertBuildStatusSuccess(j.waitForCompletion(b2));
    WorkflowJob job2 = b2.getParent();
    PipelineTriggersJobProperty triggersJobProperty2 = job2.getTriggersJobProperty();
    assertNotNull(triggersJobProperty2);
    Trigger t2 = triggersJobProperty2.getTriggers().get(0);
    assertSame(t, t2);
  }

  @TestExtension("doNotRestartEqualTriggers")
  public static class TestTrigger extends Trigger {

    private String name;

    public String getName() {
      return name;
    }

    @DataBoundSetter
    public void setName(String name) {
      this.name = name;
    }

    @DataBoundConstructor
    public TestTrigger() {}

    @Override
    public void start(Item project, boolean newInstance) {
      super.start(project, newInstance);
      System.out.println("Calling START() for " + name);
    }

    @Override
    public void stop() {
      super.stop();
      System.out.println("Calling STOP() for " + name);
      triggerLatch.countDown();
    }

    @Override
    public TriggerDescriptor getDescriptor() {
      return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("testtrigger")
    public static final class DescriptorImpl extends TriggerDescriptor {
      @Override
      public boolean isApplicable(Item item) {
        return true;
      }
    }
  }

  @TestExtension("doNotRestartEqualTriggers")
  public static class TestTriggerB extends Trigger {

    private String name;

    public String getName() {
      return name;
    }

    @DataBoundSetter
    public void setName(String name) {
      this.name = name;
    }

    @DataBoundConstructor
    public TestTriggerB() {}

    @Override
    public void start(Item project, boolean newInstance) {
      super.start(project, newInstance);
      System.out.println("Calling START() for " + name);
    }

    @Override
    public void stop() {
      super.stop();
      System.out.println("Calling STOP() for " + name);
      triggerLatch.countDown();
      triggerLatch.countDown();
      triggerLatch.countDown();
    }

    @Override
    public TriggerDescriptor getDescriptor() {
      return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("testtriggerb")
    public static final class DescriptorImpl extends TriggerDescriptor {
      @Override
      public boolean isApplicable(Item item) {
        return true;
      }
    }
  }
}
