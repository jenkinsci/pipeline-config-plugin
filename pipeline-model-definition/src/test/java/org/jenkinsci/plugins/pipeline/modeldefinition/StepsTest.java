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

import htmlpublisher.HtmlPublisherTarget;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Slave;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.testMetaStep.Curve;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/** @author Andrew Bayer */
public class StepsTest extends AbstractModelDefTest {

  private static Slave s;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setNumExecutors(10);
    s.setLabelString("some-label");
  }

  @Test
  public void nestedTreeSteps() throws Exception {
    expect("steps/nestedTreeSteps")
        .logContains("[Pipeline] { (foo)", "[Pipeline] timeout", "[Pipeline] retry", "hello")
        .go();
  }

  @Test
  public void metaStepSyntax() throws Exception {
    env(s).set();
    expect("steps/metaStepSyntax")
        .archives("msg.out", "hello world")
        .archives("msg2.out", "goodbye world")
        // Note that this test for the validator choosing a metastep over a random describable is
        // inconsistent for
        // the failure state - sometimes it works when it shouldn't for no obvious reason. A better
        // test of this that
        // will fail consistently is in pipeline-model-api's
        // DescriptorLookupCacheTest#lookupFunctionPrefersMetaStep,
        // but this is left here to be safe.
        .logContains("wrapping in a 123-gon", "hi from in rhombus")
        .go();
  }

  @Issue("JENKINS-41456")
  @Test
  public void htmlPublisher() throws Exception {
    WorkflowRun b = expect("steps/htmlPublisher").logContains("[Pipeline] { (foo)").go();

    HtmlPublisherTarget.HTMLBuildAction buildReport =
        b.getAction(HtmlPublisherTarget.HTMLBuildAction.class);
    assertNotNull(buildReport);
    assertEquals("Test Report", buildReport.getHTMLTarget().getReportName());
  }

  public static final class FakeRhombus extends AbstractDescribableImpl<FakeRhombus> {
    public final boolean foo;

    @DataBoundConstructor
    public FakeRhombus(boolean foo) {
      this.foo = foo;
    }

    @Symbol("rhombus")
    @TestExtension
    public static final class DescriptorImpl extends Descriptor<FakeRhombus> {}
  }

  public static final class Rhombus extends Curve {
    public final int n;

    @DataBoundConstructor
    public Rhombus(int n) {
      this.n = n;
    }

    @Override
    public String getDescription() {
      return n + "-gon";
    }

    @Symbol("rhombus")
    @TestExtension
    public static class DescriptorImpl extends Descriptor<Curve> {}
  }
}
