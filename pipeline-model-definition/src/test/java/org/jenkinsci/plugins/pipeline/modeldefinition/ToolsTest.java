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

import static org.junit.Assert.assertNotNull;

import hudson.model.JDK;
import hudson.model.Slave;
import hudson.tasks.Maven;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.ToolInstallations;

/** @author Andrew Bayer */
public class ToolsTest extends AbstractModelDefTest {

  private static Slave s;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setLabelString("some-label");
  }

  @Test
  public void simpleTools() throws Exception {
    expect("simpleTools").logContains("[Pipeline] { (foo)", "Apache Maven 3.0.1").go();
  }

  @Issue("JENKINS-44497")
  @Test
  public void envVarInTools() throws Exception {
    expect("environment/envVarInTools")
        .logContains("[Pipeline] { (foo)", "Apache Maven 3.0.1")
        .go();
  }

  @Test
  public void toolsInStage() throws Exception {
    expect("toolsInStage").logContains("[Pipeline] { (foo)", "Apache Maven 3.0.1").go();
  }

  @Issue("JENKINS-42338")
  @Test
  public void toolsAndAgentNone() throws Exception {
    TemporaryFolder antTmp = new TemporaryFolder();
    antTmp.create();
    ToolInstallations.configureDefaultAnt(antTmp);

    expect("toolsAndAgentNone")
        .logContains(
            "[Pipeline] { (foo)",
            "Apache Maven 3.0.1",
            "Apache Ant") // since ANT_HOME may be set, we can't actually guarantee the version
        .go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void toolsInGroup() throws Exception {
    Maven.MavenInstallation maven350 = ToolInstallations.configureMaven35();

    Maven.MavenInstallation maven301 = ToolInstallations.configureMaven3();

    j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven350, maven301);

    expect("toolsInGroup")
        .logContains(
            "Solo: Apache Maven 3.0.1",
            "First in group: Apache Maven 3.5.0",
            "Second in group: Apache Maven 3.0.1")
        .go();
  }

  @Test
  public void buildPluginParentPOM() throws Exception {
    Maven.MavenInstallation maven350 = ToolInstallations.configureMaven35();
    JDK[] jdks = j.jenkins.getDescriptorByType(JDK.DescriptorImpl.class).getInstallations();
    JDK thisJdk = null;
    for (JDK j : jdks) {
      if (j.getName().equals("default")) {
        thisJdk = j;
      }
    }
    assertNotNull("Couldn't find JDK named 'default'", thisJdk);

    expect("buildPluginParentPOM")
        .logContains(
            "[Pipeline] { (build)",
            "BUILD SUCCESS",
            "M2_HOME: " + maven350.getHome(),
            "JAVA_HOME: " + thisJdk.getHome())
        .go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void toolsWithOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    Maven.MavenInstallation maven301 = ToolInstallations.configureMaven3();

    j.jenkins.getDescriptorByType(Maven.DescriptorImpl.class).setInstallations(maven301);

    expect("toolsWithOutsideVarAndFunc").logContains("Apache Maven 3.0.1").go();
  }
}
