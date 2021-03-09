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
package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionComponent;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Result;
import java.io.Serializable;
import java.util.*;
import java.util.stream.StreamSupport;
import org.jenkinsci.plugins.pipeline.modeldefinition.CommonUtils;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.actions.WarningAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;

/**
 * Extension point for build conditions.
 *
 * @author Andrew Bayer
 */
public abstract class BuildCondition implements Serializable, ExtensionPoint {

  @Deprecated
  public boolean meetsCondition(@NonNull WorkflowRun r) {
    if (Util.isOverridden(
        BuildCondition.class,
        getClass(),
        "meetsCondition",
        WorkflowRun.class,
        Object.class,
        Throwable.class)) {
      return meetsCondition(r, null, null);
    } else {
      throw new IllegalStateException(
          getClass().getName() + " must override meetsCondition(WorkflowRun,Object,Throwable)");
    }
  }

  public boolean meetsCondition(@NonNull WorkflowRun r, Object context, Throwable error) {
    return meetsCondition(r);
  }

  @Deprecated
  public boolean meetsCondition(@NonNull Object runWrapperObj) {
    return meetsCondition(runWrapperObj, null, null);
  }

  public boolean meetsCondition(@NonNull Object runWrapperObj, Object context, Throwable error) {
    RunWrapper runWrapper = (RunWrapper) runWrapperObj;
    WorkflowRun run = (WorkflowRun) runWrapper.getRawBuild();
    return run != null && meetsCondition(run, context, error);
  }

  @Deprecated
  @NonNull
  protected final Result combineResults(@NonNull WorkflowRun run) {
    return combineResults(run, null);
  }

  @Deprecated
  @NonNull
  protected final Result combineResults(@NonNull WorkflowRun run, @CheckForNull Throwable error) {
    return combineResults(run, error, null);
  }

  @NonNull
  protected final Result combineResults(
      @NonNull WorkflowRun run, @CheckForNull Throwable error, @CheckForNull Object context) {
    return BuildCondition.getCombinedResult(run, error, context);
  }

  @CheckForNull
  protected Result getExecutionResult(@NonNull WorkflowRun r) {
    return BuildCondition.getFlowExecutionResult(r);
  }

  public abstract String getDescription();

  /**
   * All the registered {@link BuildCondition}s.
   *
   * @return A list of all registered {@link BuildCondition}s.
   */
  public static ExtensionList<BuildCondition> all() {
    return ExtensionList.lookup(BuildCondition.class);
  }

  public static List<String> getOrderedConditionNames() {
    List<String> orderedConditions = new ArrayList<>();

    List<ExtensionComponent<BuildCondition>> extensionComponents =
        new ArrayList<>(all().getComponents());
    Collections.sort(extensionComponents);

    for (ExtensionComponent<BuildCondition> extensionComponent : extensionComponents) {
      BuildCondition b = extensionComponent.getInstance();
      Set<String> symbolValues = SymbolLookup.getSymbolValue(b);

      if (!symbolValues.isEmpty()) {
        orderedConditions.add(symbolValues.iterator().next());
      }
    }

    return orderedConditions;
  }

  public static Map<String, BuildCondition> getConditionMethods() {
    Map<String, BuildCondition> conditions = new HashMap<>();

    for (BuildCondition b : all()) {

      Set<String> symbolValues = SymbolLookup.getSymbolValue(b);

      if (!symbolValues.isEmpty()) {
        conditions.put(symbolValues.iterator().next(), b);
      }
    }
    return conditions;
  }

  @Deprecated
  @NonNull
  public static Result getCombinedResult(@NonNull WorkflowRun run, @CheckForNull Throwable error) {
    return getCombinedResult(run, error, null);
  }

  @NonNull
  public static Result getCombinedResult(
      @NonNull WorkflowRun run, @CheckForNull Throwable error, @CheckForNull Object context) {
    Result errorResult = Result.SUCCESS;
    if (context instanceof String) {
      String stageName = (String) context;
      List<FlowNode> startAndEnd =
          CommonUtils.findPossiblyUnfinishedEndNodeForCurrentStage(stageName, run.getExecution());
      if (startAndEnd.size() == 2 && startAndEnd.get(0) != null && startAndEnd.get(1) != null) {
        // TODO: Remove this in favor of moving StatusAndTiming#findWarningBetween public.
        DepthFirstScanner scanner = new DepthFirstScanner();
        if (scanner.setup(startAndEnd.get(1), Collections.singletonList(startAndEnd.get(0)))) {
          WarningAction warningAction =
              StreamSupport.stream(scanner.spliterator(), false)
                  .map(node -> node.getPersistentAction(WarningAction.class))
                  .filter(Objects::nonNull)
                  .max(Comparator.comparing(warning -> warning.getResult().ordinal))
                  .orElse(null);
          if (warningAction != null) {
            errorResult = warningAction.getResult();
          }
        }
      }
    }
    Result execResult = getFlowExecutionResult(run);
    Result prevResult = run.getResult();
    if (prevResult == null) {
      prevResult = Result.SUCCESS;
    }
    if (execResult == null) {
      execResult = Result.SUCCESS;
    }
    if (error != null) {
      if (error instanceof FlowInterruptedException) {
        errorResult = ((FlowInterruptedException) error).getResult();
      } else {
        errorResult = Result.FAILURE;
      }
    }
    return execResult.combine(prevResult).combine(errorResult);
  }

  @CheckForNull
  public static Result getFlowExecutionResult(@NonNull WorkflowRun r) {
    FlowExecution execution = r.getExecution();
    if (execution instanceof CpsFlowExecution) {
      return ((CpsFlowExecution) execution).getResult();
    } else {
      return r.getResult();
    }
  }

  private static final long serialVersionUID = 1L;
}
