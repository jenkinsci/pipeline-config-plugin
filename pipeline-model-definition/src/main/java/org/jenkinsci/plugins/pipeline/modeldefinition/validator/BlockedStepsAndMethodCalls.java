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

package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.Map;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

@Extension
public class BlockedStepsAndMethodCalls extends DeclarativeValidatorContributor {
  /** Get the map of step names to rejection messages. Exposed statically for testing purposes. */
  public static Map<String, String> blockedInSteps() {
    return ImmutableMap.of(
        "stage",
        Messages.BlockedStepsAndMethodCalls_BlockedSteps_Stage(),
        "properties",
        Messages.BlockedStepsAndMethodCalls_BlockedSteps_Properties(),
        "parallel",
        Messages.BlockedStepsAndMethodCalls_BlockedSteps_Parallel());
  }

  /**
   * Get the map of method or step names to rejection messages. Exposed statically for testing
   * purposes and for use in more granular validation of job properties vs declarative options vs
   * wrapper steps.
   */
  public static Map<String, String> blockedInMethodCalls() {
    return new ImmutableMap.Builder<String, String>()
        .put("node", Messages.BlockedStepsAndMethodCalls_BlockedSteps_Node())
        .putAll(blockedInSteps())
        .build();
  }

  @Override
  @CheckForNull
  public String validateElement(
      @NonNull ModelASTMethodCall method, @CheckForNull FlowExecution execution) {
    if (method.getName() != null) {
      if (blockedInMethodCalls().keySet().contains(method.getName())) {
        return org.jenkinsci.plugins.pipeline.modeldefinition.Messages
            .ModelValidatorImpl_BlockedStep(
                method.getName(), blockedInMethodCalls().get(method.getName()));
      }
    }

    return null;
  }

  @Override
  @CheckForNull
  public String validateElement(@NonNull ModelASTStep step, @CheckForNull FlowExecution execution) {
    if (step.getName() != null) {
      if (blockedInSteps().keySet().contains(step.getName())) {
        return org.jenkinsci.plugins.pipeline.modeldefinition.Messages
            .ModelValidatorImpl_BlockedStep(step.getName(), blockedInSteps().get(step.getName()));
      }
    }

    return null;
  }
}
