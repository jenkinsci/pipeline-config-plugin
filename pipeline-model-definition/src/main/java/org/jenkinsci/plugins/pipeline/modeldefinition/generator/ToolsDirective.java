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
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

public class ToolsDirective extends AbstractDirective<ToolsDirective> {
  public static final String TOOLS_DELIMITER = "::::";

  private final List<SymbolAndName> tools = new ArrayList<>();

  @DataBoundConstructor
  public ToolsDirective(List<SymbolAndName> tools) {
    if (tools != null) {
      this.tools.addAll(tools);
    }
  }

  public List<SymbolAndName> getTools() {
    return tools;
  }

  private List<ToolInstallation> getToolInstallations() {
    return tools.stream()
        .map(ToolsDirective::installationFromParam)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static ToolInstallation installationFromParam(@NonNull SymbolAndName symbolAndName) {
    String symbol = symbolAndName.getSymbol();
    String name = symbolAndName.getName();
    if (!StringUtils.isEmpty(symbol) && !StringUtils.isEmpty(name)) {
      ToolDescriptor d = SymbolLookup.get().find(ToolDescriptor.class, symbol);
      if (d != null) {
        for (ToolInstallation t : d.getInstallations()) {
          if (name.equals(t.getName())) {
            return t;
          }
        }
      }
    }
    return null;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<ToolsDirective> {
    @Override
    @NonNull
    public String getName() {
      return "tools";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Tools";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      return ExtensionList.lookup(ToolDescriptor.class).stream()
          .filter(
              t ->
                  t.getInstallations().length > 0
                      && DirectiveGenerator.getSymbolForDescriptor(t) != null)
          .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull ToolsDirective directive) {
      StringBuilder result = new StringBuilder("tools {\n");
      if (!directive.getTools().isEmpty()) {
        for (ToolInstallation tool : directive.getToolInstallations()) {
          result
              .append(DirectiveGenerator.getSymbolForDescriptor(tool.getDescriptor()))
              .append(" ");
          result.append(Snippetizer.object2Groovy(tool.getName())).append("\n");
        }
      } else {
        result.append("// No valid tools specified\n");
      }
      result.append("}\n");

      return result.toString();
    }
  }

  @Restricted(NoExternalUse.class)
  public static final class SymbolAndName {
    private String symbolAndName;
    private String symbol;
    private String name;

    @DataBoundConstructor
    public SymbolAndName(String symbolAndName) {
      String[] s = StringUtils.split(symbolAndName, TOOLS_DELIMITER);
      this.symbol = s[0];
      this.name = s[1];
      this.symbolAndName = symbolAndName;
    }

    public String getSymbolAndName() {
      return symbolAndName;
    }

    public String getName() {
      return name;
    }

    public String getSymbol() {
      return symbol;
    }
  }
}
