/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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

import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.testMetaStep.Curve;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class DescriptorLookupCacheTest {
  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void lookupFunctionPrefersMetaStep() throws Exception {
    Descriptor<? extends Describable> d =
        DescriptorLookupCache.getPublicCache().lookupFunction("rhombus");
    assertEquals(Rhombus.DescriptorImpl.class, d.getClass());
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
    public static class DescriptorImpl extends Descriptor<Curve> {
      @Nonnull
      @Override
      public String getDisplayName() {
        return "rhombus curve";
      }
    }
  }
}
