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
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public class StagesDirective extends AbstractDirective<StagesDirective> {
  private List<StageDirective> stages;

  @DataBoundConstructor
  public StagesDirective(List<StageDirective> stages) {
    this.stages = stages;
  }

  public List<StageDirective> getStages() {
    return stages;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<StagesDirective> {
    @Override
    @NonNull
    public String getName() {
      return "stages";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Stages";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      List<Descriptor> descriptors = new ArrayList<>();
      descriptors.add(Jenkins.get().getDescriptorByType(StageDirective.DescriptorImpl.class));

      return descriptors;
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull StagesDirective stages) {
      StringBuilder result = new StringBuilder("stages {\n");
      if (stages.stages != null) {
        stages.stages.stream().forEach(a -> result.append(a.toGroovy(false)).append("\n"));
      }
      result.append("}\n");
      return result.toString();
    }
  }
}
