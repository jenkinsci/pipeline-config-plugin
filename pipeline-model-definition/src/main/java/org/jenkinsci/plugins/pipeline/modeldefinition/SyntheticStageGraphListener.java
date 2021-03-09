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

import static java.util.logging.Level.WARNING;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Actionable;
import hudson.model.InvisibleAction;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.pipeline.SyntheticStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;

@Extension
public final class SyntheticStageGraphListener implements GraphListener {
  private static final Logger LOGGER =
      Logger.getLogger(SyntheticStageGraphListener.class.getName());

  private final transient Map<FlowExecution, Boolean> declarativeRuns = new WeakHashMap<>();

  @Override
  public void onNewHead(FlowNode node) {
    if (node != null
        && node instanceof StepStartNode
        && ((StepStartNode) node).getDescriptor() instanceof StageStep.DescriptorImpl) {
      if (isDeclarativeRun(node.getExecution())) {
        LabelAction label = node.getAction(LabelAction.class);
        if (label != null
            && (SyntheticStageNames.preStages().contains(label.getDisplayName())
                || SyntheticStageNames.postStages().contains(label.getDisplayName()))) {
          if (SyntheticStageNames.preStages().contains(label.getDisplayName())) {
            attachTag(node, SyntheticStage.getPre());
          }
          if (SyntheticStageNames.postStages().contains(label.getDisplayName())) {
            attachTag(node, SyntheticStage.getPost());
          }
        }
      }
    }
  }

  private void attachTag(FlowNode currentNode, String syntheticContext) {
    TagsAction tagsAction = currentNode.getAction(TagsAction.class);
    if (tagsAction == null) {
      tagsAction = new TagsAction();
      tagsAction.addTag(SyntheticStage.TAG_NAME, syntheticContext);
      currentNode.addAction(tagsAction);
    } else if (tagsAction.getTagValue(SyntheticStage.TAG_NAME) == null) {
      tagsAction.addTag(SyntheticStage.TAG_NAME, syntheticContext);
      try {
        currentNode.save();
      } catch (IOException e) {
        LOGGER.log(WARNING, "failed to save actions for FlowNode id=" + currentNode.getId(), e);
      }
    }
  }

  private synchronized boolean isDeclarativeRun(@NonNull FlowExecution execution) {
    if (!declarativeRuns.containsKey(execution)) {
      boolean isDeclarative = false;
      try {
        FlowExecutionOwner owner = execution.getOwner();
        if (owner != null && owner.getExecutable() instanceof Actionable) {
          if (((Actionable) owner.getExecutable()).getAction(ExecutionModelAction.class) != null) {
            isDeclarative = true;
          }
        }
      } catch (IOException e) {
        LOGGER.log(Level.WARNING, "Error loading WorkflowRun for FlowNode: {0}", e);
      }

      declarativeRuns.put(execution, isDeclarative);
    }

    return declarativeRuns.get(execution);
  }

  @Deprecated
  public static class GraphListenerAction extends InvisibleAction {}
}
