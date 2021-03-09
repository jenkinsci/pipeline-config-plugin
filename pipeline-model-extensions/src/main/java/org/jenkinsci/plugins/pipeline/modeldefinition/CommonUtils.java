/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

import com.google.common.base.Predicate;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.PasswordParameterDefinition;
import hudson.util.Secret;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class CommonUtils {
  public static Predicate<FlowNode> isStageWithOptionalName(final String stageName) {
    return new Predicate<FlowNode>() {
      @Override
      public boolean apply(@Nullable FlowNode input) {
        if (input != null) {
          if (input instanceof StepStartNode
              && ((StepStartNode) input).getDescriptor() instanceof StageStep.DescriptorImpl
              && (stageName == null || input.getDisplayName().equals(stageName))) {
            // This is a true stage.
            return true;
          } else {
            final ThreadNameAction action = input.getAction(ThreadNameAction.class);
            if (input.getAction(LabelAction.class) != null
                && action != null
                && (stageName == null || action.getThreadName().equals(stageName))) {
              return true;
            }
          }
        }

        return false;
      }
    };
  }

  public static Predicate<FlowNode> isStageWithOptionalName() {
    return isStageWithOptionalName(null);
  }

  public static List<FlowNode> findPossiblyUnfinishedEndNodeForCurrentStage(
      String stageName, FlowExecution execution) {
    if (execution == null) {
      CpsThread thread = CpsThread.current();
      execution = thread.getExecution();
    }

    ForkScanner scanner = new ForkScanner();

    FlowNode stage =
        scanner.findFirstMatch(
            execution.getCurrentHeads(), null, isStageWithOptionalName(stageName));

    FlowNode finalNode =
        execution.getCurrentHeads().stream()
            .filter(h -> isSomewhereWithinStage(stage).apply(h))
            .findFirst()
            .orElse(null);

    return Arrays.asList(stage, finalNode);
  }

  public static List<FlowNode> findPossiblyUnfinishedEndNodeForCurrentStage(String stageName) {
    return findPossiblyUnfinishedEndNodeForCurrentStage(stageName, null);
  }

  /**
   * This will return true for flow nodes in *child* stages, not just the immediate enclosing stage.
   *
   * @param stageStartNode
   * @return A predicate that returns true if the applied input is somewhere within the given stage
   */
  public static Predicate<FlowNode> isSomewhereWithinStage(final FlowNode stageStartNode) {
    return new Predicate<FlowNode>() {
      @Override
      public boolean apply(@Nullable FlowNode input) {
        if (input != null && stageStartNode instanceof BlockStartNode) {
          return input.getEnclosingBlocks().contains(stageStartNode);
        }

        return false;
      }
    };
  }

  @Whitelisted
  @Restricted(NoExternalUse.class)
  public static <T> T instantiateDescribable(Class<T> c, Map<String, ?> args) {
    DescribableModel<T> model = new DescribableModel<T>(c);
    // Special case for JENKINS-63499.
    if (model.getType().equals(PasswordParameterDefinition.class)
        && model.getParameter("defaultValueAsSecret") != null) {
      args =
          copyMapReplacingEntry(
              args, "defaultValue", "defaultValueAsSecret", String.class, Secret::fromString);
    }

    return model.instantiate(args);
  }

  /** Copy a map, replacing the entry with the specified key if it matches the specified type. */
  public static <T> Map<String, Object> copyMapReplacingEntry(
      Map<String, ?> map,
      String oldKey,
      String newKey,
      Class<T> requiredValueType,
      Function<T, Object> replacer) {
    Map<String, Object> newMap = new TreeMap<>();
    for (Map.Entry<String, ?> entry : map.entrySet()) {
      if (entry.getKey().equals(oldKey) && requiredValueType.isInstance(entry.getValue())) {
        newMap.put(newKey, replacer.apply(requiredValueType.cast(entry.getValue())));
      } else {
        newMap.put(entry.getKey(), entry.getValue());
      }
    }

    return newMap;
  }
}
