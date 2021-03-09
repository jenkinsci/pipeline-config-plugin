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
import hudson.ExtensionList;
import hudson.model.Descriptor;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

public abstract class DirectiveDescriptor<T extends AbstractDirective<T>> extends Descriptor<T> {
  @NonNull
  public boolean isTopLevel() {
    return true;
  }

  @NonNull
  public abstract String getName();

  @NonNull
  public abstract String getDisplayName();

  @NonNull
  public abstract List<Descriptor> getDescriptors();

  @NonNull
  public static ExtensionList<DirectiveDescriptor> all() {
    return ExtensionList.lookup(DirectiveDescriptor.class);
  }

  @NonNull
  public abstract String toGroovy(@NonNull T directive);

  @NonNull
  public final String toIndentedGroovy(@NonNull T directive) {
    return ModelASTPipelineDef.toIndentedGroovy(toGroovy(directive));
  }

  public static String symbolForDescriptor(@NonNull Descriptor d) {
    if (d instanceof StepDescriptor) {
      return ((StepDescriptor) d).getFunctionName();
    } else {
      Set<String> symbols = SymbolLookup.getSymbolValue(d);

      if (symbols.isEmpty()) {
        return null;
      } else {
        return symbols.iterator().next();
      }
    }
  }
}
