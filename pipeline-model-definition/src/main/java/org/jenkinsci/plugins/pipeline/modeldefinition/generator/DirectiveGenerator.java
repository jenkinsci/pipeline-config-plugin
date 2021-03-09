/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.generator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Item;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.branch.OrganizationFolder;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.cps.SnippetizerLink;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.AbstractWorkflowMultiBranchProjectFactory;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public class DirectiveGenerator extends Snippetizer {
  private static final Logger LOGGER = Logger.getLogger(DirectiveGenerator.class.getName());

  public static final String ACTION_URL = "directive-generator";

  @Restricted(NoExternalUse.class)
  public static final String GENERATE_URL = ACTION_URL + "/generateDirective";

  @Override
  public String getUrlName() {
    return ACTION_URL;
  }

  @NonNull
  public List<DirectiveDescriptor> getDirectives() {
    return DirectiveDescriptor.all();
  }

  @Restricted(DoNotUse.class) // accessed via REST API
  public HttpResponse doGenerateDirective(StaplerRequest req, @QueryParameter String json)
      throws Exception {
    // TODO is there not an easier way to do this? Maybe Descriptor.newInstancesFromHeteroList on a
    // one-element JSONArray?
    JSONObject jsonO = JSONObject.fromObject(json);
    Jenkins j = Jenkins.get();
    Class<?> c = j.getPluginManager().uberClassLoader.loadClass(jsonO.getString("stapler-class"));
    DirectiveDescriptor descriptor =
        (DirectiveDescriptor) j.getDescriptor(c.asSubclass(AbstractDirective.class));
    if (descriptor == null) {
      return HttpResponses.plainText("<could not find " + c.getName() + ">");
    }
    Object o;
    try {
      o = descriptor.newInstance(req, jsonO);
    } catch (RuntimeException x) { // e.g. IllegalArgumentException
      return HttpResponses.plainText(Functions.printThrowable(x));
    }
    try {
      String groovy = descriptor.toIndentedGroovy((AbstractDirective) o);
      return HttpResponses.plainText(groovy);
    } catch (UnsupportedOperationException x) {
      LOGGER.log(Level.WARNING, "failed to render " + json, x);
      return HttpResponses.plainText(x.getMessage());
    }
  }

  @Restricted(NoExternalUse.class)
  public static String mapToClosure(Map<String, ?> args) {
    StringBuilder result = new StringBuilder("{\n");
    for (Map.Entry<String, ?> arg : args.entrySet()) {
      if (!(arg.getValue() instanceof String && arg.getValue().equals(""))) {
        result.append(arg.getKey()).append(" ");
        if (arg.getValue() instanceof Map) {
          result.append(mapToClosure((Map<String, ?>) arg.getValue()));
        } else if (arg.getValue() != null) {
          result
              .append(
                  Snippetizer.object2Groovy(
                      arg.getValue(), arg.getValue() instanceof UninstantiatedDescribable))
              .append("\n");
        }
      }
    }
    result.append("}\n");
    return result.toString();
  }

  @Restricted(NoExternalUse.class) // For jelly and internal use
  public static String getSymbolForDescriptor(Descriptor d) {
    if (d instanceof StepDescriptor) {
      return ((StepDescriptor) d).getFunctionName();
    } else {
      Set<String> symbols = SymbolLookup.getSymbolValue(d);
      if (!symbols.isEmpty()) {
        return symbols.iterator().next();
      } else {
        return "(unknown)";
      }
    }
  }

  @Restricted(DoNotUse.class)
  @Extension
  public static class PerWorkflowJobAdder extends TransientActionFactory<WorkflowJob> {

    @Override
    public Class<WorkflowJob> type() {
      return WorkflowJob.class;
    }

    @Override
    @NonNull
    public Collection<? extends Action> createFor(@NonNull WorkflowJob target) {
      if (target.hasPermission(Item.EXTENDED_READ)) {
        return Collections.singleton(new DirectiveGenerator());
      } else {
        return Collections.emptySet();
      }
    }
  }

  @Restricted(DoNotUse.class)
  @Extension
  public static class PerOrgFolderAdder extends TransientActionFactory<OrganizationFolder> {

    @Override
    public Class<OrganizationFolder> type() {
      return OrganizationFolder.class;
    }

    @Override
    @NonNull
    public Collection<? extends Action> createFor(@NonNull OrganizationFolder target) {
      if (target.getProjectFactories().get(AbstractWorkflowMultiBranchProjectFactory.class) != null
          && target.hasPermission(Item.EXTENDED_READ)) {
        return Collections.singleton(new DirectiveGenerator());
      } else {
        return Collections.emptySet();
      }
    }
  }

  @Restricted(DoNotUse.class)
  @Extension
  public static class PerMultiBranchFolderAdder
      extends TransientActionFactory<WorkflowMultiBranchProject> {

    @Override
    public Class<WorkflowMultiBranchProject> type() {
      return WorkflowMultiBranchProject.class;
    }

    @Override
    @NonNull
    public Collection<? extends Action> createFor(@NonNull WorkflowMultiBranchProject target) {
      if (target.hasPermission(Item.EXTENDED_READ)) {
        return Collections.singleton(new DirectiveGenerator());
      } else {
        return Collections.emptySet();
      }
    }
  }

  @Extension(ordinal = 950L)
  public static class DeclarativeDirectivesLink extends SnippetizerLink {
    @Override
    @NonNull
    public String getUrl() {
      return ACTION_URL;
    }

    @Override
    @NonNull
    public String getIcon() {
      return "icon-gear2 icon-md";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return Messages.DirectiveGenerator_DeclarativeDirectivesLink_displayName();
    }
  }

  @Extension(ordinal = 925L)
  public static class DeclarativeOnlineDocsLink extends SnippetizerLink {
    @Override
    @NonNull
    public String getUrl() {
      return "https://jenkins.io/doc/book/pipeline/syntax/";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return Messages.DirectiveGenerator_DeclarativeOnlineDocsLink_displayName();
    }

    @Override
    public boolean inNewWindow() {
      return true;
    }
  }
}
