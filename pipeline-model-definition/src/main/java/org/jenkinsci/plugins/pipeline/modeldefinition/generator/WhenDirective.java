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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.kohsuke.stapler.DataBoundConstructor;

public class WhenDirective extends AbstractDirective<WhenDirective> {
  private DeclarativeStageConditional conditional;
  private boolean beforeAgent;
  private boolean beforeInput;
  private boolean beforeOptions;

  @DataBoundConstructor
  public WhenDirective(
      DeclarativeStageConditional conditional,
      boolean beforeAgent,
      boolean beforeInput,
      boolean beforeOptions) {
    this.conditional = conditional;
    this.beforeAgent = beforeAgent;
    this.beforeInput = beforeInput;
    this.beforeOptions = beforeOptions;
  }

  public DeclarativeStageConditional getConditional() {
    return conditional;
  }

  public boolean isBeforeAgent() {
    return beforeAgent;
  }

  public boolean isBeforeInput() {
    return beforeInput;
  }

  public boolean isBeforeOptions() {
    return beforeOptions;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<WhenDirective> {
    @Override
    @NonNull
    public String getName() {
      return "when";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "When Condition";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      // For some reason, returning forGenerator directly won't cast from
      // DeclarativeStageConditionalDescriptor to Descriptor. Fun.
      return new ArrayList<>(DeclarativeStageConditionalDescriptor.forGenerator());
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull WhenDirective directive) {
      if (directive.conditional != null) {
        UninstantiatedDescribable ud = UninstantiatedDescribable.from(directive.conditional);
        DescribableModel<? extends DeclarativeStageConditional> model = ud.getModel();
        if (model != null) {
          Map<String, Object> args = new HashMap<>();
          // Hack to null out non-required fields for UI prettiness.
          for (String argKey : ud.getArguments().keySet()) {
            DescribableParameter p = model.getParameter(argKey);
            if (p == null
                || p.isRequired()
                || !(String.class.equals(p.getErasedType()))
                || !ud.getArguments().get(argKey).equals("")) {
              args.put(argKey, ud.getArguments().get(argKey));
            }
          }

          StringBuilder result = new StringBuilder();
          result.append("when {\n");

          try {
            result.append(conditionalToGroovy(model.instantiate(args)));
          } catch (Exception e) {
            result.append("// ERROR TRANSLATING CONDITIONAL: ").append(e).append("\n");
          }

          if (directive.isBeforeOptions()) {
            result.append("beforeOptions true\n");
          }
          if (directive.isBeforeInput()) {
            result.append("beforeInput true\n");
          }
          if (directive.isBeforeAgent()) {
            result.append("beforeAgent true\n");
          }
          result.append("}\n");
          return result.toString();
        }
      }

      return "// No valid when condition defined\n";
    }

    @NonNull
    private String conditionalToGroovy(@NonNull DeclarativeStageConditional<?> conditional) {
      DeclarativeStageConditionalDescriptor descriptor = conditional.getDescriptor();

      if (descriptor.getAllowedChildrenCount() == 0) {
        return Snippetizer.object2Groovy(UninstantiatedDescribable.from(conditional)) + "\n";
      } else {
        StringBuilder result = new StringBuilder();
        result.append(descriptor.getName()).append(" {\n");

        for (DeclarativeStageConditional c : conditional.getChildren()) {
          result.append(conditionalToGroovy(c));
        }
        result.append("}\n");
        return result.toString();
      }
    }
  }
}
