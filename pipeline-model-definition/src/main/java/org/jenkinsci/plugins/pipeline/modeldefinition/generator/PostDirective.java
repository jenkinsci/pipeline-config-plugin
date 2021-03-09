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
import java.util.*;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.kohsuke.stapler.DataBoundConstructor;

public class PostDirective extends AbstractDirective<PostDirective> {
  private final List<String> conditions = new ArrayList<>();

  @DataBoundConstructor
  public PostDirective(List<String> conditions) {
    if (conditions != null) {
      this.conditions.addAll(conditions);
    }
  }

  public List<String> getConditions() {
    return conditions;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<PostDirective> {
    @Override
    @NonNull
    public String getName() {
      return "post";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Post Stage or Build Conditions";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      return Collections.emptyList();
    }

    @NonNull
    public Set<Map.Entry<String, String>> getPossibleConditions() {
      Map<String, String> conditionMap = new HashMap<>();

      for (BuildCondition bc : BuildCondition.all()) {
        Set<String> symbols = SymbolLookup.getSymbolValue(bc);
        if (!symbols.isEmpty()) {
          conditionMap.put(symbols.iterator().next(), bc.getDescription());
        }
      }

      return conditionMap.entrySet();
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull PostDirective directive) {
      StringBuilder result = new StringBuilder("post {\n");
      if (!directive.getConditions().isEmpty()) {
        for (String bc : directive.getConditions()) {
          result.append(bc).append(" {\n");
          result.append(
              "// One or more steps need to be included within each condition's block.\n");
          result.append("}\n");
        }
      } else {
        result.append("// No post conditions specified\n");
      }
      result.append("}\n");

      return result.toString();
    }
  }
}
