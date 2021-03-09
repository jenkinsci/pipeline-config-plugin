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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.jenkinsci.Symbol;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class MultipleUnnamedParametersTest extends AbstractModelDefTest {

  // Note that we're doing this in its own class and not doing JSON validation because of the hassle
  // of the test
  // extension - we'll get an error due to an unknown option type if we don't have the test
  // extension defined for the
  // whole class.

  @Test
  public void tooManyUnnamedParameters() throws Exception {
    expectError("tooManyUnnamedParameters")
        .logContains(Messages.ModelValidatorImpl_TooManyUnnamedParameters("multiArgCtorProp"))
        .go();
  }

  public static class MultiArgCtorProp extends JobProperty<Job<?, ?>> {
    private String first;
    private int second;

    @DataBoundConstructor
    public MultiArgCtorProp(String first, int second) {
      this.first = first;
      this.second = second;
    }

    public String getFirst() {
      return first;
    }

    public int getSecond() {
      return second;
    }

    @TestExtension
    @Symbol("multiArgCtorProp")
    public static class MultiArgCtorPropDescriptor extends JobPropertyDescriptor {
      @Override
      @NonNull
      public String getDisplayName() {
        return "Test property with multiple parameters to DataBoundConstructor";
      }
    }
  }
}
