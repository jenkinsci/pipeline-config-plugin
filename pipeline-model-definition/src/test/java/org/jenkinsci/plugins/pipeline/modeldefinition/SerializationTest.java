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

import static org.junit.Assert.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.BooleanParameterDefinition;
import hudson.model.Describable;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Slave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.LogRotator;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import java.util.Arrays;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.XStreamPickle;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;

/**
 * Note that in practice, only {@link #serializationEnvGString} fails, but it felt best to cover the
 * other possible cases as well.
 */
@Issue("JENKINS-42498")
public class SerializationTest extends AbstractModelDefTest {

  private static Slave s;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setNumExecutors(4);
    s.setLabelString("some-label test");
    s.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true")));
  }

  @Test
  public void serializationEnvGString() throws Exception {
    expect("serialization/serializationEnvGString")
        .logContains("[Pipeline] { (foo)", "_UNDERSCORE is VALID")
        .logMatches("FOO is test\\d+foo")
        .go();
  }

  @Test
  public void serializationParametersGString() throws Exception {
    WorkflowRun b =
        expect("serialization/serializationParametersGString")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
            .go();

    WorkflowJob p = b.getParent();

    ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(pdp);

    assertEquals(1, pdp.getParameterDefinitions().size());
    assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
    BooleanParameterDefinition bpd =
        (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
    assertEquals(p.getDisplayName(), bpd.getName());
    assertTrue(bpd.isDefaultValue());
  }

  @Test
  public void serializationAgentGString() throws Exception {
    expect("serialization/serializationAgentGString")
        .logContains("[Pipeline] { (foo)", "ONAGENT=true")
        .go();
  }

  @Test
  public void serializationAgentNestedGString() throws Exception {
    expect("serialization/serializationAgentNestedGString")
        .logContains("[Pipeline] { (foo)", "ONAGENT=true")
        .go();
  }

  @Test
  public void serializationJobPropsGString() throws Exception {
    WorkflowRun b =
        expect("serialization/serializationJobPropsGString")
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
    assertEquals(Integer.parseInt(p.getDisplayName().substring(4)), lr.getNumToKeep());
  }

  @Test
  public void serializationLibrariesGString() throws Exception {
    otherRepo.init();
    otherRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
    otherRepo.write("vars/myecho.txt", "Says something very special!");
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    LibraryConfiguration firstLib =
        new LibraryConfiguration(
            "echo-utils",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

    thirdRepo.init();
    thirdRepo.write("vars/whereFrom.groovy", "def call() {echo 'from another library'}");
    thirdRepo.write("vars/whereFrom.txt", "Says where it's from!");
    thirdRepo.git("add", "vars");
    thirdRepo.git("commit", "--message=init");
    LibraryConfiguration secondLib =
        new LibraryConfiguration(
            "test",
            new SCMSourceRetriever(
                new GitSCMSource(null, thirdRepo.toString(), "", "*", "", true)));
    secondLib.setDefaultVersion("master");
    GlobalLibraries.get().setLibraries(Arrays.asList(firstLib, secondLib));

    expect("serialization/serializationLibrariesGString")
        .logContains("something special", "from another library")
        .go();
  }

  @Test
  public void serializationTriggersGString() throws Exception {
    WorkflowRun b =
        expect("serialization/serializationTriggersGString")
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
  public void serializationWhenBranchGString() throws Exception {
    expect("serialization/serializationWhenBranchGString")
        .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
        .go();
  }

  @Test
  public void serializationWhenEnvGString() throws Exception {
    expect("serialization/serializationWhenEnvGString")
        .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)", "World")
        .go();
  }

  @TestExtension
  public static class XStreamPickleFactory extends SingleTypedPickleFactory<Describable<?>> {

    @Override
    @NonNull
    protected Pickle pickle(@NonNull Describable<?> d) {
      return new XStreamPickle(d);
    }
  }
}
