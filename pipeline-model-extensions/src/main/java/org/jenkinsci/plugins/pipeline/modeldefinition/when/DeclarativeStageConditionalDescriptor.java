/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import java.util.*;
import java.util.stream.Collectors;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.withscript.WithScriptDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;

/** Base descriptor for {@link DeclarativeStageConditional}. */
public abstract class DeclarativeStageConditionalDescriptor<
        S extends DeclarativeStageConditional<S>>
    extends WithScriptDescriptor<S> {

  /**
   * How many nested conditions are allowed. -1 for unlimited, 0 for none, anything greater than 0
   * for requiring exactly that many nested conditions.
   */
  public int getAllowedChildrenCount() {
    return 0;
  }

  /**
   * Whether this conditional can be rendered in the Directive Generator. Defaults to whether
   * there's a config page - which we determine by checking to see if {@link #getConfigPage()}
   * returns something other than its default "config.jelly". It will if there's an actual
   * config.jelly or config.groovy either for this class or an ancestor.
   */
  public boolean inDirectiveGenerator() {
    return !"config.jelly".equals(getConfigPage());
  }

  /** Whether this conditional is an invisible global conditional. Defaults to false. */
  public boolean isInvisible() {
    return false;
  }

  public abstract Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original);

  /**
   * Get all {@link DeclarativeStageConditionalDescriptor}s.
   *
   * @return a list of all {@link DeclarativeStageConditionalDescriptor}s registered, except for
   *     invisible global conditionals.
   */
  public static List<DeclarativeStageConditionalDescriptor> all() {
    return allIncludingInvisible().stream()
        .filter(d -> !d.isInvisible())
        .collect(Collectors.toList());
  }

  public static List<DeclarativeStageConditionalDescriptor> forGenerator() {
    return all().stream()
        .filter(DeclarativeStageConditionalDescriptor::inDirectiveGenerator)
        .collect(Collectors.toList());
  }

  private static List<DeclarativeStageConditionalDescriptor> allIncludingInvisible() {
    ExtensionList<DeclarativeStageConditionalDescriptor> descs =
        ExtensionList.lookup(DeclarativeStageConditionalDescriptor.class);
    return descs.stream()
        .sorted(Comparator.comparing(DeclarativeStageConditionalDescriptor::getName))
        .collect(Collectors.toList());
  }

  public static List<DeclarativeStageConditionalDescriptor> allInvisible() {
    return allIncludingInvisible().stream()
        .filter(DeclarativeStageConditionalDescriptor::isInvisible)
        .collect(Collectors.toList());
  }

  public static List<String> allNames() {
    List<DeclarativeStageConditionalDescriptor> all = all();
    List<String> names = new ArrayList<>(all.size());
    for (DeclarativeStageConditionalDescriptor descriptor : all) {
      names.add(descriptor.getName());
    }
    return names;
  }

  /**
   * Get a map of name-to-{@link DescribableModel} of all known/registered descriptors.
   *
   * @return A map of name-to-{@link DescribableModel}s
   */
  public static Map<String, DescribableModel> getDescribableModels() {
    Map<String, DescribableModel> models = new HashMap<>();

    for (DeclarativeStageConditionalDescriptor d : all()) {
      for (String s : SymbolLookup.getSymbolValue(d)) {
        models.put(s, new DescribableModel<>(d.clazz));
      }
    }

    return models;
  }
  /**
   * Get the descriptor for a given name or null if not found.
   *
   * @param name The name for the descriptor to look up
   * @return The corresponding descriptor or null if not found.
   */
  @Nullable
  public static DeclarativeStageConditionalDescriptor byName(@NonNull String name) {
    return (DeclarativeStageConditionalDescriptor)
        SymbolLookup.get().findDescriptor(DeclarativeStageConditional.class, name);
  }
}
