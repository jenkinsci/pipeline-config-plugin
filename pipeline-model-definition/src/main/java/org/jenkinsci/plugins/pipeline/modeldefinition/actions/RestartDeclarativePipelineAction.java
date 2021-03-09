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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.model.Queue;
import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.util.*;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.causes.RestartDeclarativePipelineCause;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

@ExportedBean
public class RestartDeclarativePipelineAction implements Action {

  private final Run run;

  private RestartDeclarativePipelineAction(Run run) {
    this.run = run;
  }

  @Override
  public String getDisplayName() {
    return Messages.Restart_from_Stage();
  }

  @Override
  public String getIconFileName() {
    return isRestartEnabled()
        ? "plugin/pipeline-model-definition/images/24x24/restart-stage.png"
        : null;
  }

  @Override
  public String getUrlName() {
    return isRestartEnabled() ? "restart" : null;
  }

  /* accessible to Jelly */ public Run getOwner() {
    return run;
  }

  @CheckForNull
  private CpsFlowExecution getExecution() {
    FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
    if (owner == null) {
      return null;
    }
    FlowExecution exec = owner.getOrNull();
    return exec instanceof CpsFlowExecution ? (CpsFlowExecution) exec : null;
  }

  @Exported
  public boolean isRestartEnabled() {
    ExecutionModelAction executionModelAction = run.getAction(ExecutionModelAction.class);

    return executionModelAction != null
        && !run.isBuilding()
        && run.hasPermission(Item.BUILD)
        && run.getParent().isBuildable()
        && getExecution() != null;
  }

  public Api getApi() {
    return new Api(this);
  }

  @Restricted(NoExternalUse.class)
  @RequirePOST
  public HttpResponse doRestartPipeline(@QueryParameter String stageName) {
    Map<String, String> result = new HashMap<>();
    result.put("success", "false");

    if (isRestartEnabled()) {
      try {
        run(stageName);

        result.put("success", "true");
        result.put("message", "ok");
      } catch (IllegalStateException ise) {
        result.put("message", "Failure restarting from stage: " + ise);
      }
    } else {
      result.put("message", "not allowed to restart");
    }

    return HttpResponses.okJSON(result);
  }

  @Restricted(DoNotUse.class)
  @RequirePOST
  public void doRestart(StaplerRequest req, StaplerResponse rsp)
      throws ServletException, IOException {
    if (!isRestartEnabled()) {
      throw new AccessDeniedException(
          "not allowed to restart"); // AccessDeniedException2 requires us to look up the specific
      // Permission
    }
    JSONObject form = req.getSubmittedForm();
    String stageName = Util.fixEmpty(form.getString("stageName"));

    try {
      run(stageName);
    } catch (IllegalStateException ise) {
      throw new Failure("Failure restarting from stage: " + ise);
    }

    rsp.sendRedirect(
        "../.."); // back to WorkflowJob; new build might not start instantly so cannot redirect to
    // it
  }

  @Exported
  public List<String> getRestartableStages() {
    List<String> stages = new ArrayList<>();
    FlowExecution execution = getExecution();
    if (execution != null) {
      ExecutionModelAction execAction = run.getAction(ExecutionModelAction.class);
      if (execAction != null) {
        if (execAction.getStages() != null) {
          for (ModelASTStage s : execAction.getStages().getStages()) {
            if (!Utils.stageHasStatusOf(
                s.getName(),
                execution,
                StageStatus.getSkippedForFailure(),
                StageStatus.getSkippedForUnstable())) {
              stages.add(s.getName());
            }
          }
        }
      }
    }
    return stages;
  }

  public String getCheckUrl() {
    return Jenkins.get().getRootUrl() + run.getUrl() + getUrlName() + "/" + "checkStageName";
  }

  public FormValidation doCheckStageName(@QueryParameter String value) {
    String s = Util.fixEmptyAndTrim(value);
    if (s == null || s.equals("")) {
      return FormValidation.error(Messages.RestartDeclarativePipelineAction_NullStageName());
    }
    if (!getRestartableStages().contains(s)) {
      return FormValidation.error(
          Messages.RestartDeclarativePipelineAction_StageNameNotPresent(
              s, run.getFullDisplayName()));
    }
    return FormValidation.ok();
  }

  public Queue.Item run(String stageName) {
    if (stageName == null || stageName.equals("")) {
      throw new IllegalStateException(Messages.RestartDeclarativePipelineAction_NullStageName());
    }

    if (!run.hasPermission(Item.BUILD) || !run.getParent().isBuildable()) {
      throw new IllegalStateException(
          Messages.RestartDeclarativePipelineAction_ProjectNotBuildable(
              run.getParent().getFullName()));
    }

    if (run.isBuilding()) {
      throw new IllegalStateException(
          Messages.RestartDeclarativePipelineAction_OriginRunIncomplete(run.getFullDisplayName()));
    }

    ExecutionModelAction execAction = run.getAction(ExecutionModelAction.class);
    if (execAction == null) {
      throw new IllegalStateException(
          Messages.RestartDeclarativePipelineAction_OriginWasNotDeclarative(
              run.getFullDisplayName()));
    }

    if (!getRestartableStages().contains(stageName)) {
      throw new IllegalStateException(
          Messages.RestartDeclarativePipelineAction_StageNameNotPresent(
              stageName, run.getFullDisplayName()));
    }
    List<Action> actions = new ArrayList<>();
    CpsFlowExecution execution = getExecution();
    if (execution == null) {
      throw new IllegalStateException(
          Messages.RestartDeclarativePipelineAction_OriginRunMissingExecution(
              run.getFullDisplayName()));
    }

    actions.add(new RestartFlowFactoryAction(run.getExternalizableId()));
    actions.add(
        new CauseAction(
            new Cause.UserIdCause(), new RestartDeclarativePipelineCause(run, stageName)));
    return ParameterizedJobMixIn.scheduleBuild2(
        run.getParent(), 0, actions.toArray(new Action[actions.size()]));
  }

  @Extension
  public static class Factory extends TransientActionFactory<WorkflowRun> {

    @Override
    public Class<WorkflowRun> type() {
      return WorkflowRun.class;
    }

    @Override
    @NonNull
    public Collection<? extends Action> createFor(@NonNull WorkflowRun run) {
      return Collections.<Action>singleton(new RestartDeclarativePipelineAction(run));
    }
  }
}
