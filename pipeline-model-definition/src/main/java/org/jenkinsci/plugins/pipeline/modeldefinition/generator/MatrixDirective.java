/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

public class MatrixDirective extends AbstractDirective<MatrixDirective> {

  private List<AbstractDirective> axes;

  @DataBoundConstructor
  public MatrixDirective(List<AbstractDirective> axes) {
    this.axes = axes;
  }

  public List<AbstractDirective> getAxes() {
    return axes;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<MatrixDirective> {
    @Override
    @NonNull
    public String getName() {
      return "matrix";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Matrix";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      List<Descriptor> descriptors = new ArrayList<>();
      descriptors.add(Jenkins.get().getDescriptorByType(AxesDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(ExcludesDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(AgentDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(EnvironmentDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(InputDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(OptionsDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(PostDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(ToolsDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(WhenDirective.DescriptorImpl.class));
      descriptors.add(Jenkins.get().getDescriptorByType(StagesDirective.DescriptorImpl.class));

      return descriptors;
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull MatrixDirective matrix) {
      StringBuilder result = new StringBuilder("matrix {\n");
      if (matrix.axes != null) {
        matrix.axes.stream().forEach(a -> result.append(a.toGroovy(false)).append("\n"));
      }
      result.append("}\n");
      return result.toString();
    }
  }
}
