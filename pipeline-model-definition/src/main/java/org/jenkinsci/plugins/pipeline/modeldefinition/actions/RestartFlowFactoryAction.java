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

package org.jenkinsci.plugins.pipeline.modeldefinition.actions;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import hudson.model.Run;
import java.io.IOException;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsFlowFactoryAction2;
import org.jenkinsci.plugins.workflow.flow.FlowCopier;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

public class RestartFlowFactoryAction extends InvisibleAction
    implements CpsFlowFactoryAction2, Queue.QueueAction {
  private String originRunId;

  public RestartFlowFactoryAction(@NonNull String originRunId) {
    this.originRunId = originRunId;
  }

  public String getOriginRunId() {
    return originRunId;
  }

  /** Not allowing restart attempts to be collapsed into the same build. */
  @Override
  public boolean shouldSchedule(List<Action> actions) {
    return true;
  }

  @Override
  public CpsFlowExecution create(
      FlowDefinition def, FlowExecutionOwner owner, List<? extends Action> actions)
      throws IOException {
    Run original = Run.fromExternalizableId(originRunId);
    String origScript = null;
    boolean origSandbox = true;
    if (original instanceof FlowExecutionOwner.Executable) {
      FlowExecutionOwner originalOwner =
          ((FlowExecutionOwner.Executable) original).asFlowExecutionOwner();
      if (originalOwner != null) {
        try {
          for (FlowCopier copier : ExtensionList.lookup(FlowCopier.class)) {
            copier.copy(originalOwner, owner);
          }
        } catch (InterruptedException x) {
          throw new IOException("Failed to copy metadata", x);
        }
        FlowExecution origExecution = originalOwner.getOrNull();
        if (origExecution instanceof CpsFlowExecution) {
          origScript = ((CpsFlowExecution) origExecution).getScript();
          origSandbox = ((CpsFlowExecution) origExecution).isSandbox();
        }
      }
    }

    if (origScript != null) {
      return new CpsFlowExecution(origScript, origSandbox, owner);
    } else {
      return null;
    }
  }
}
