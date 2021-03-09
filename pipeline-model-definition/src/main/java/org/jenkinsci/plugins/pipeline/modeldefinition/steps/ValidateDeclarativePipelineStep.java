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
package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.Serializable;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Step that will validate a string containing a Declarative Pipeline.
 *
 * @author Andrew Bayer
 */
public final class ValidateDeclarativePipelineStep extends AbstractStepImpl
    implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String path;

  @DataBoundConstructor
  public ValidateDeclarativePipelineStep(String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }

  @Extension
  public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

    public DescriptorImpl() {
      super(ValidateDeclarativePipelineStepExecution.class);
    }

    @Override
    public String getFunctionName() {
      return "validateDeclarativePipeline";
    }

    @Override
    public String getDisplayName() {
      return "Validate a file containing a Declarative Pipeline";
    }
  }

  public static final class ValidateDeclarativePipelineStepExecution
      extends AbstractSynchronousNonBlockingStepExecution<Boolean> {

    @Inject private transient ValidateDeclarativePipelineStep step;

    @StepContextParameter private transient FilePath cwd;

    @StepContextParameter private transient TaskListener listener;

    @Override
    public Boolean run() throws Exception {
      if (StringUtils.isEmpty(step.getPath())) {
        listener.getLogger().println("No Declarative Pipeline file specified.");
        return false;
      } else {
        FilePath f = cwd.child(step.getPath());
        if (!f.exists() || f.isDirectory()) {
          listener
              .getLogger()
              .println("Declarative Pipeline file '" + step.getPath() + "' does not exist.");
          return false;
        } else {
          String text = f.readToString();
          if (StringUtils.isEmpty(text)) {
            listener
                .getLogger()
                .println("Declarative Pipeline file '" + step.getPath() + "' is empty.");
            return false;
          } else {
            try {
              ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(text);
              if (pipelineDef != null) {
                listener
                    .getLogger()
                    .println("Declarative Pipeline file '" + step.getPath() + "' is valid.");
                return true;
              } else {
                listener
                    .getLogger()
                    .println(
                        "Declarative Pipeline file '"
                            + step.getPath()
                            + "' does not contain the 'pipeline' step.");
                return false;
              }
            } catch (Exception e) {
              listener
                  .getLogger()
                  .println(
                      "Error(s) validating Declarative Pipeline file '"
                          + step.getPath()
                          + "' - "
                          + e.toString());
              return false;
            }
          }
        }
      }
    }

    private static final long serialVersionUID = 1L;
  }
}
