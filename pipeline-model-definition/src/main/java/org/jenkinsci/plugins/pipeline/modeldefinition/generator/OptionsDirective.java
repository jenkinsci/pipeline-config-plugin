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

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.JobPropertyDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.BlockedStepsAndMethodCalls;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class OptionsDirective extends AbstractDirective<OptionsDirective> {
  public static final List<String> ADDITIONAL_BLOCKED_STEPS =
      ImmutableList.of(
          "script", "ws", "withEnv", "withCredentials", "withContext", "waitUntil", "catchError");

  private List<Describable> options = new ArrayList<>();

  @DataBoundConstructor
  public OptionsDirective(List<Describable> options) {
    if (options != null) {
      this.options.addAll(options);
    }
  }

  @NonNull
  public List<Describable> getOptions() {
    return options;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<OptionsDirective> {
    @Override
    @NonNull
    public String getName() {
      return "options";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Options";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      return getDescriptorsForContext(false);
    }

    @NonNull
    public List<Descriptor> getDescriptorsForContext(boolean inStage) {
      List<Descriptor> descriptors = new ArrayList<>();

      for (Descriptor d : ExtensionList.lookup(JobPropertyDescriptor.class)) {
        Set<String> symbolValue = SymbolLookup.getSymbolValue(d);
        if (!symbolValue.isEmpty()) {
          boolean blockedSymbol = false;
          for (String symbol : symbolValue) {
            if (Options.getBLOCKED_PROPERTIES().contains(symbol)) {
              blockedSymbol = true;
            }
          }
          if (!blockedSymbol && !inStage) {
            descriptors.add(d);
          }
        }
      }

      for (DeclarativeOptionDescriptor d :
          ExtensionList.lookup(DeclarativeOptionDescriptor.class)) {
        if (!SymbolLookup.getSymbolValue(d).isEmpty()) {
          if ((!inStage && !d.isStageOnly()) || d.canUseInStage()) {
            descriptors.add(d);
          }
        }
      }

      for (StepDescriptor sd : StepDescriptor.all()) {
        if (sd.takesImplicitBlockArgument()
            && !(BlockedStepsAndMethodCalls.blockedInMethodCalls()
                .containsKey(sd.getFunctionName()))
            && !(sd.getRequiredContext().contains(Launcher.class))
            && !(sd.getRequiredContext().contains(FilePath.class))
            && !(ADDITIONAL_BLOCKED_STEPS.contains(sd.getFunctionName()))) {
          descriptors.add(sd);
        }
      }

      return descriptors.stream()
          .filter(d -> DirectiveDescriptor.symbolForDescriptor(d) != null)
          .sorted(Comparator.comparing(d -> DirectiveDescriptor.symbolForDescriptor(d)))
          .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull OptionsDirective directive) {
      StringBuilder result = new StringBuilder("options {\n");
      for (Describable d : directive.options) {
        if (d instanceof Step) {
          String origGroovy = Snippetizer.object2Groovy(d);
          // Need to remove the block bit since we're cheating.
          result.append(
              origGroovy.substring(0, origGroovy.length() - " {\n    // some block\n}".length()));
          result.append("\n");
        } else {
          result.append(Snippetizer.object2Groovy(UninstantiatedDescribable.from(d)));
          result.append("\n");
        }
      }
      result.append("}\n");
      return result.toString();
    }
  }
}
