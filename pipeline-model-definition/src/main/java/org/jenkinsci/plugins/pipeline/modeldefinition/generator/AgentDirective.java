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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.kohsuke.stapler.DataBoundConstructor;

public class AgentDirective extends AbstractDirective<AgentDirective> {
  private DeclarativeAgent agent;

  @DataBoundConstructor
  public AgentDirective(DeclarativeAgent agent) {
    this.agent = agent;
  }

  public DeclarativeAgent getAgent() {
    return agent;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<AgentDirective> {
    @Override
    @NonNull
    public String getName() {
      return "agent";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Agent";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      List<Descriptor> descriptors = new ArrayList<>();
      List<DeclarativeAgentDescriptor> descs =
          DeclarativeAgentDescriptor.all().stream()
              .sorted(Comparator.comparing(DeclarativeAgentDescriptor::getName))
              .collect(Collectors.toList());
      for (DeclarativeAgentDescriptor td : descs) {
        if (!SymbolLookup.getSymbolValue(td).isEmpty()) {
          descriptors.add(td);
        }
      }

      return descriptors;
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull AgentDirective directive) {
      if (directive.agent != null) {
        DeclarativeAgentDescriptor desc = directive.agent.getDescriptor();

        UninstantiatedDescribable ud = UninstantiatedDescribable.from(directive.agent);
        DescribableModel model = ud.getModel();
        if (model != null) {
          StringBuilder result = new StringBuilder();
          if (DeclarativeAgentDescriptor.zeroArgModels().containsKey(desc.getName())) {
            // agent none or agent any
            result.append("agent ").append(desc.getName());
          } else {
            result.append("agent {\n");
            if (DeclarativeAgentDescriptor.noRequiredArgsModels().containsKey(desc.getName())
                && ud.getArguments().entrySet().stream()
                    .allMatch(
                        e ->
                            e.getValue() == null
                                || (e.getValue() instanceof String && e.getValue().equals("")))) {
              // agent { dockerfile true }
              result.append(desc.getName()).append(" true\n");
            } else if (model.hasSingleRequiredParameter() && ud.getArguments().size() == 1) {
              // agent { label 'foo' } or agent { docker 'image' }
              result.append(Snippetizer.object2Groovy(ud)).append("\n");
            } else {
              // Multiple arguments etc
              result
                  .append(desc.getName())
                  .append(" ")
                  .append(DirectiveGenerator.mapToClosure(ud.getArguments()));
            }
            result.append("}");
          }
          result.append("\n");
          return result.toString();
        }
      }

      return "// No valid agent defined\n";
    }
  }
}
