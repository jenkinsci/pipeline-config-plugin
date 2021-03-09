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
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/** @author Andrew Bayer */
public class AgentTest extends AbstractModelDefTest {

  private static Slave s;
  private static Slave s2;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setLabelString("some-label");
    s.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first")));
    s.setNumExecutors(2);

    s2 = j.createOnlineSlave();
    s2.setLabelString("other-label");
    s2.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second")));
  }

  @Issue("JENKINS-37932")
  @Test
  public void agentAny() throws Exception {
    expect("agent/agentAny").logContains("[Pipeline] { (foo)", "THIS WORKS").go();
  }

  // Also covers basic agentLabel usage.
  @Test
  public void noCheckoutScmInWrongContext() throws Exception {
    expect("agent/noCheckoutScmInWrongContext")
        .runFromRepo(false)
        .logContains("[Pipeline] { (foo)", "ONAGENT=true")
        .go();
  }

  @Test
  public void agentNone() throws Exception {
    expect(Result.FAILURE, "agent/agentNone")
        .logContains(Messages.ModelInterpreter_NoNodeContext())
        .go();
  }

  @Test
  public void agentNoneWithNode() throws Exception {
    expect("agent/agentNoneWithNode").logContains("[Pipeline] { (foo)", "ONAGENT=true").go();
  }

  @Test
  public void multipleVariablesForAgent() throws Exception {
    expect("agent/multipleVariablesForAgent")
        .logContains(
            "[Pipeline] { (foo)",
            "ONAGENT=true",
            "Running in labelAndOtherField with otherField = banana",
            "And nested: foo: monkey, bar: false")
        .go();
  }

  @Ignore(
      "Still not sure yet whether we'll ever fix JENKINS-43911, but wanted to have a test here for if we do")
  @Issue("JENKINS-43911")
  @Test
  public void agentFromEnv() throws Exception {
    expect("agent/agentFromEnv").logContains("WHICH_AGENT=first", "WHICH_AGENT=second").go();
  }

  @Issue("JENKINS-43911")
  @Test
  public void agentFromParentEnv() throws Exception {
    expect("agent/agentFromParentEnv").logContains("WHICH_AGENT=first", "WHICH_AGENT=second").go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void agentOnGroup() throws Exception {
    expect("agent/agentOnGroup")
        .logContains(
            "Solo stage agent: first",
            "First other stage agent: second",
            "Second other stage agent: first")
        .go();
  }

  @Issue("JENKINS-54919")
  @Test
  public void paramInAgentLabel() throws Exception {
    expect("paramInAgentLabel").logContains("[Pipeline] { (foo)", "ONAGENT=true").go();
  }

  @Issue("JENKINS-41118")
  @Test
  public void inCustomWorkspace() throws Exception {
    expect("agent/inCustomWorkspace").logMatches("Workspace dir is .*some-sub-dir").go();
  }

  @Issue("JENKINS-41118")
  @Test
  public void inRelativeCustomWorkspace() throws Exception {
    onAllowedOS(PossibleOS.LINUX, PossibleOS.MAC);
    expect("agent/inRelativeCustomWorkspace")
        .logMatches("Workspace dir is .*relative/custom2/workspace3")
        .go();
  }

  @Issue("JENKINS-41118")
  @Test
  public void inAbsoluteCustomWorkspace() throws Exception {
    // Since we're using a Unix path, only run on a Unix environment
    onAllowedOS(PossibleOS.LINUX, PossibleOS.MAC);
    try {
      expect("agent/inAbsoluteCustomWorkspace")
          .logContains("Workspace dir is /tmp/some-sub-dir")
          .go();
    } finally {
      FileUtils.deleteDirectory(new File("/tmp/some-sub-dir"));
    }
  }

  @Issue("JENKINS-41118")
  @Test
  public void inCustomWorkspaceInStage() throws Exception {
    expect("agent/inCustomWorkspaceInStage")
        .logMatches("Workspace dir is .*some-other-sub-dir")
        .go();
  }
}
