/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(NoExternalUse.class)
@Extension
public class DescriptorLookupCache {
  private transient Map<String, StepDescriptor> stepMap;
  private transient Map<String, DescribableModel<? extends Step>> modelMap;
  private transient Map<String, Descriptor<? extends Describable>> describableMap;
  private transient Map<String, DescribableModel<? extends Describable>> describableModelMap;

  public static DescriptorLookupCache getPublicCache() {
    return ExtensionList.lookup(DescriptorLookupCache.class).get(0);
  }

  @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED) // Prevents potential leakage on reload
  public static void invalidateGlobalCache() {
    getPublicCache().invalidateAll();
  }

  public DescriptorLookupCache() {
    invalidateAll();
  }

  public synchronized void invalidateAll() {
    this.stepMap = new LinkedHashMap<>();
    this.modelMap = new LinkedHashMap<>();
    this.describableMap = new LinkedHashMap<>();
    this.describableModelMap = new LinkedHashMap<>();
  }

  public synchronized DescribableModel<? extends Step> modelForStep(String n) {
    if (!modelMap.containsKey(n)) {
      final StepDescriptor descriptor = lookupStepDescriptor(n);
      Class<? extends Step> c = (descriptor == null ? null : descriptor.clazz);
      modelMap.put(n, c != null ? new DescribableModel<>(c) : null);
    }

    return modelMap.get(n);
  }

  public synchronized DescribableModel<? extends Describable> modelForDescribable(String n) {
    return modelForDescribable(n, null);
  }

  public synchronized DescribableModel<? extends Describable> modelForDescribable(
      String n, @CheckForNull Class<? extends Describable> describable) {
    if (describable == null) {
      if (!describableModelMap.containsKey(n)) {
        final Descriptor<? extends Describable> function = lookupFunction(n);
        Class<? extends Describable> c = (function == null ? null : function.clazz);
        describableModelMap.put(n, c != null ? new DescribableModel<>(c) : null);
      }

      return describableModelMap.get(n);
    } else {
      // TODO: Cache models for parent describables too
      final Descriptor<? extends Describable> function = lookupFunction(n, describable);
      Class<? extends Describable> c = (function == null ? null : function.clazz);
      return c != null ? new DescribableModel<>(c) : null;
    }
  }

  public synchronized StepDescriptor lookupStepDescriptor(String n) {
    if (stepMap.isEmpty()) {
      for (StepDescriptor d : StepDescriptor.all()) {
        stepMap.put(d.getFunctionName(), d);
      }
    }
    return stepMap.get(n);
  }

  public synchronized Descriptor<? extends Describable> lookupFunction(String n) {
    return lookupFunction(n, null);
  }

  public synchronized Descriptor<? extends Describable> lookupFunction(
      String n, @CheckForNull Class<? extends Describable> describable) {
    if (n == null) {
      return null;
    } else if (describable == null) {
      if (!describableMap.containsKey(n)) {
        Descriptor<? extends Describable> d = null;

        // Prefer metasteps, falling back on any old describable.
        for (StepDescriptor metaStep : StepDescriptor.metaStepsOf(n)) {
          d = SymbolLookup.get().findDescriptor(metaStep.getMetaStepArgumentType(), n);
          if (d != null) {
            break;
          }
        }
        // Fall back on a non-metastep describable
        if (d == null) {
          d = SymbolLookup.get().findDescriptor(Describable.class, n);
        }
        describableMap.put(n, d);
      }

      return describableMap.get(n);
    } else {
      // TODO: Switch to caching when we're looking up specific describables
      return SymbolLookup.get().findDescriptor(describable, n);
    }
  }

  public synchronized Descriptor<? extends Describable> lookupStepFirstThenFunction(String name) {
    return lookupStepFirstThenFunction(name, null);
  }

  public synchronized Descriptor<? extends Describable> lookupFunctionFirstThenStep(String name) {
    return lookupFunctionFirstThenStep(name, null);
  }

  public synchronized DescribableModel<? extends Describable> modelForStepFirstThenFunction(
      String name) {
    return modelForStepFirstThenFunction(name, null);
  }

  public synchronized DescribableModel<? extends Describable> modelForFunctionFirstThenStep(
      String name) {
    return modelForFunctionFirstThenStep(name, null);
  }

  public synchronized Descriptor<? extends Describable> lookupStepFirstThenFunction(
      String name, Class<? extends Describable> describable) {
    return lookupStepDescriptor(name) != null
        ? lookupStepDescriptor(name)
        : lookupFunction(name, describable);
  }

  public synchronized Descriptor<? extends Describable> lookupFunctionFirstThenStep(
      String name, Class<? extends Describable> describable) {
    return lookupFunction(name, describable) != null
        ? lookupFunction(name, describable)
        : lookupStepDescriptor(name);
  }

  public synchronized DescribableModel<? extends Describable> modelForStepFirstThenFunction(
      String name, Class<? extends Describable> describable) {
    Descriptor<? extends Describable> desc = lookupStepDescriptor(name);
    DescribableModel<? extends Describable> model = null;

    if (desc != null) {
      model = modelForStep(name);
    } else {
      desc = lookupFunction(name, describable);
      if (desc != null) {
        model = modelForDescribable(name, describable);
      }
    }

    return model;
  }

  public synchronized DescribableModel<? extends Describable> modelForFunctionFirstThenStep(
      String name, Class<? extends Describable> describable) {
    Descriptor<? extends Describable> desc = lookupFunction(name, describable);
    DescribableModel<? extends Describable> model = null;

    if (desc != null) {
      model = modelForDescribable(name, describable);
    } else {
      desc = lookupStepDescriptor(name);
      if (desc != null) {
        model = modelForStep(name);
      }
    }

    return model;
  }

  public boolean stepTakesClosure(Descriptor d) {
    if (d instanceof StepDescriptor) {
      return ((StepDescriptor) d).takesImplicitBlockArgument();
    } else {
      return false;
    }
  }
}
