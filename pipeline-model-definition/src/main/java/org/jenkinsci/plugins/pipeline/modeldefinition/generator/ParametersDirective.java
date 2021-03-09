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
import hudson.ExtensionList;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.PasswordParameterDefinition;
import hudson.util.Secret;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.pipeline.modeldefinition.CommonUtils;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.kohsuke.stapler.DataBoundConstructor;

public class ParametersDirective extends AbstractDirective<ParametersDirective> {
  private List<ParameterDefinition> parameters = new ArrayList<>();

  @DataBoundConstructor
  public ParametersDirective(List<ParameterDefinition> parameters) {
    if (parameters != null) {
      this.parameters.addAll(parameters);
    }
  }

  @NonNull
  public List<ParameterDefinition> getParameters() {
    return parameters;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<ParametersDirective> {
    @Override
    @NonNull
    public String getName() {
      return "parameters";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Parameters";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      return ExtensionList.lookup(ParameterDefinition.ParameterDescriptor.class).stream()
          .filter(d -> DirectiveDescriptor.symbolForDescriptor(d) != null)
          .sorted(Comparator.comparing(d -> DirectiveDescriptor.symbolForDescriptor(d)))
          .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull ParametersDirective directive) {
      StringBuilder result = new StringBuilder("parameters {\n");
      for (ParameterDefinition param : directive.parameters) {
        result.append(Snippetizer.object2Groovy(customUninstantiate(param)));
        result.append("\n");
      }
      result.append("}\n");
      return result.toString();
    }

    /** Compatibility hack for JENKINS-63516. */
    public UninstantiatedDescribable customUninstantiate(ParameterDefinition param) {
      UninstantiatedDescribable step = UninstantiatedDescribable.from(param);
      if (param instanceof PasswordParameterDefinition
          && DescribableModel.of(PasswordParameterDefinition.class).getParameter("defaultValue")
              == null) {
        Map<String, Object> newParamArgs =
            CommonUtils.copyMapReplacingEntry(
                step.getArguments(),
                "defaultValueAsSecret",
                "defaultValue",
                Secret.class,
                Secret::getPlainText);
        return step.withArguments(newParamArgs);
      }
      return step;
    }
  }
}
