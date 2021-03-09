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
package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import hudson.Extension;
import java.io.Serializable;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Step that marks the use of "scripting features" that goes beyond the configy subset.
 *
 * <p>This marker assists the validation, but at runtime it doesn't do anything. It just executes
 * the body normally.
 *
 * @author Andrew Bayer
 */
public final class ScriptStep extends AbstractStepImpl implements Serializable {

  private static final long serialVersionUID = 1L;

  @DataBoundConstructor
  public ScriptStep() {}

  @Extension
  public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

    public DescriptorImpl() {
      super(ScriptStepExecution.class);
    }

    @Override
    public String getFunctionName() {
      return "script";
    }

    @Override
    public String getDisplayName() {
      return "Run arbitrary Pipeline script";
    }

    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }
  }

  public static final class ScriptStepExecution extends AbstractStepExecutionImpl {

    @Override
    public boolean start() throws Exception {

      getContext().newBodyInvoker().withCallback(BodyExecutionCallback.wrap(getContext())).start();
      return false;
    }

    private static final long serialVersionUID = 1L;
  }
}
