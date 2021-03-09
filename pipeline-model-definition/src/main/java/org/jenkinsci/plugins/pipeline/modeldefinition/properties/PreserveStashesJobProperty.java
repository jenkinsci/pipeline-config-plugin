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

package org.jenkinsci.plugins.pipeline.modeldefinition.properties;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import hudson.util.FormValidation;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.OptionalJobProperty;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKeyValueOrMethodCallPair;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodArg;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.DeclarativeValidatorContributor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class PreserveStashesJobProperty extends OptionalJobProperty<WorkflowJob> {

  private static final Logger LOGGER = Logger.getLogger(PreserveStashesJobProperty.class.getName());

  public static final int MAX_SAVED_STASHES = 50;

  private int buildCount = 1;

  @DataBoundConstructor
  public PreserveStashesJobProperty() {}

  public int getBuildCount() {
    return buildCount;
  }

  @DataBoundSetter
  public void setBuildCount(int buildCount) {
    if (buildCount > MAX_SAVED_STASHES) {
      throw new IllegalArgumentException("buildCount must be between 1 and " + MAX_SAVED_STASHES);
    }
    this.buildCount = buildCount;
  }

  @Extension
  @Symbol("preserveStashes")
  public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
    @Override
    @NonNull
    public String getDisplayName() {
      return "Preserve stashes from completed builds";
    }

    public FormValidation doCheckBuildCount(@QueryParameter int value) {
      if (value < 0) {
        return FormValidation.error("Must be greater than or equal to 0");
      } else if (value > MAX_SAVED_STASHES) {
        return FormValidation.error("Must be " + MAX_SAVED_STASHES + " or less");
      } else {
        return FormValidation.ok();
      }
    }
  }

  @Extension
  public static final class SaveStashes extends StashManager.StashBehavior {

    @Override
    public boolean shouldClearAll(@NonNull Run<?, ?> build) {
      if (build instanceof WorkflowRun) {
        WorkflowRun r = (WorkflowRun) build;
        WorkflowJob j = r.getParent();
        PreserveStashesJobProperty prop = j.getProperty(PreserveStashesJobProperty.class);
        if (prop != null) {
          int bc = prop.getBuildCount();
          if (bc > 0) {
            for (WorkflowRun recentRun : j.getBuilds().completedOnly().limit(bc)) {
              if (recentRun != null && recentRun.equals(r)) {
                return false;
              }
            }
          }
        }
      }
      return true;
    }
  }

  @Extension
  public static final class StashClearingListener extends RunListener<Run<?, ?>> {
    @Override
    public void onCompleted(Run<?, ?> r, TaskListener listener) {
      if (r instanceof WorkflowRun) {
        WorkflowJob j = ((WorkflowRun) r).getParent();
        PreserveStashesJobProperty prop = j.getProperty(PreserveStashesJobProperty.class);
        if (prop != null && prop.getBuildCount() > 0) {
          // The "+1" is to ensure that we look at one more than the maximum possible number of
          // builds with saved stashes.
          for (WorkflowRun build : j.getBuilds().completedOnly().limit(MAX_SAVED_STASHES + 1)) {
            try {
              StashManager.maybeClearAll(build);
            } catch (IOException x) {
              LOGGER.log(Level.WARNING, "failed to clean up stashes from " + build, x);
            }
          }
        }
      }
    }
  }

  @Extension
  public static class ValidatorImpl extends DeclarativeValidatorContributor {
    @Override
    @CheckForNull
    public String validateElement(
        @NonNull ModelASTOption option, @CheckForNull FlowExecution execution) {
      if (option.getName() != null && option.getName().equals("preserveStashes")) {
        for (ModelASTMethodArg arg : option.getArgs()) {
          if (arg instanceof ModelASTKeyValueOrMethodCallPair) {
            ModelASTKeyValueOrMethodCallPair namedArg = (ModelASTKeyValueOrMethodCallPair) arg;
            if (namedArg.getKey().getKey().equals("buildCount")) {
              if (namedArg.getValue() instanceof ModelASTValue
                  && ((ModelASTValue) namedArg.getValue()).getValue() instanceof Integer) {
                Integer v = (Integer) ((ModelASTValue) namedArg.getValue()).getValue();

                if (v < 1 || v > MAX_SAVED_STASHES) {
                  return Messages.PreserveStashesJobProperty_ValidatorImpl_InvalidBuildCount(
                      MAX_SAVED_STASHES);
                }
              }
            }
          }
        }
      }

      return null;
    }
  }
}
