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
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class StageDirective extends AbstractDirective<StageDirective> {
  public enum StageContentType {
    STEPS,
    PARALLEL,
    STAGES,
    MATRIX;

    public String getName() {
      // TODO: This could probably be easier, but I wanted to use a localized string and couldn't
      // think of anything better.
      if (this == STEPS) {
        return Messages.StageDirective_Steps_name();
      } else if (this == PARALLEL) {
        return Messages.StageDirective_Parallel_name();
      } else if (this == STAGES) {
        return Messages.StageDirective_Stages_name();
      } else if (this == MATRIX) {
        return Messages.StageDirective_Matrix_name();
      } else {
        return "(unknown)";
      }
    }
  }

  private List<AbstractDirective> directives = new ArrayList<>();
  private String name;
  private StageContentType contentType;

  @DataBoundConstructor
  public StageDirective(
      List<AbstractDirective> directives, @NonNull String name, StageContentType contentType) {
    if (directives != null) {
      this.directives.addAll(directives);
    }
    this.name = name;
    this.contentType = contentType;
  }

  @NonNull
  public String getName() {
    return name;
  }

  /** What the content of this stage is - currently steps or parallel */
  public StageContentType getContentType() {
    return contentType;
  }

  @NonNull
  public List<AbstractDirective> getDirectives() {
    return directives;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<StageDirective> {
    @Override
    @NonNull
    public String getName() {
      return "stage";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Stage";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      List<Descriptor> descriptors = new ArrayList<>();
      descriptors.add(Jenkins.get().getDescriptorByType(AgentDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(InputDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(EnvironmentDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(OptionsDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(WhenDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(ToolsDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(PostDirective.DescriptorImpl.class));

      return descriptors;
    }

    public FormValidation doCheckName(@QueryParameter String value) {
      if (StringUtils.isEmpty(value)) {
        return FormValidation.error("Stage name must be provided.");
      } else {
        return FormValidation.ok();
      }
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull StageDirective directive) {
      StringBuilder result = new StringBuilder("stage(");
      result.append("'").append(directive.name).append("') {\n");
      switch (directive.contentType) {
        case STEPS:
          result.append("steps {\n");
          result.append("// One or more steps need to be included within the steps block.\n");
          result.append("}\n");
          break;
        case PARALLEL:
          result.append("parallel {\n");
          result.append("// One or more stages need to be included within the parallel block.\n");
          result.append("}\n");
          break;
        case STAGES:
          result.append("stages {\n");
          result.append("// One or more stages need to be included within the stages block.\n");
          result.append("}\n");
          break;
        case MATRIX:
          result.append("matrix {\n");
          result.append("// matrix need to be included.\n");
          result.append("}\n");
          break;
        default:
          result.append(
              "// Unknown stage content - only steps and parallel currently available.\n");
      }
      for (AbstractDirective d : directive.directives) {
        result.append("\n").append(d.toGroovy(false));
      }
      result.append("}\n");
      return result.toString();
    }
  }
}
