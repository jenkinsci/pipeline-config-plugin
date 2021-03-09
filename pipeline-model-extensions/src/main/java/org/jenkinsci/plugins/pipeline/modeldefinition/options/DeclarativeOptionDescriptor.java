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
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.options;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Descriptor;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.structs.SymbolLookup;

public abstract class DeclarativeOptionDescriptor extends Descriptor<DeclarativeOption> {

  public @NonNull String getName() {
    Set<String> symbolValues = SymbolLookup.getSymbolValue(this);
    if (symbolValues.isEmpty()) {
      throw new IllegalArgumentException(
          "Declarative Option descriptor class "
              + this.getClass().getName()
              + " does not have a @Symbol and does not override getName().");
    }
    return symbolValues.iterator().next();
  }

  /** If true, this option can be used in stages and is relevant there. */
  public boolean canUseInStage() {
    return false;
  }

  /** If true, this option can *only* be used in stages. */
  public boolean isStageOnly() {
    return false;
  }

  /**
   * Get all {@link DeclarativeOptionDescriptor}s.
   *
   * @return a list of all {@link DeclarativeOptionDescriptor}s registered.`
   */
  public static List<DeclarativeOptionDescriptor> all() {
    ExtensionList<DeclarativeOptionDescriptor> descs =
        ExtensionList.lookup(DeclarativeOptionDescriptor.class);
    return descs.stream()
        .sorted(Comparator.comparing(DeclarativeOptionDescriptor::getName))
        .collect(Collectors.toList());
  }
}
