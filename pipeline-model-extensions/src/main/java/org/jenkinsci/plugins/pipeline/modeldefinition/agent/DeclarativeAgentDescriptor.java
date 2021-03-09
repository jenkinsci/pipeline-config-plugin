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
package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.pipeline.modeldefinition.withscript.WithScriptDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;

/**
 * Descriptor for {@link DeclarativeAgent}.
 *
 * @author Andrew Bayer
 */
public abstract class DeclarativeAgentDescriptor<A extends DeclarativeAgent<A>>
    extends WithScriptDescriptor<A> {

  /**
   * Get all {@link DeclarativeAgentDescriptor}s.
   *
   * @return a list of all {@link DeclarativeAgentDescriptor}s registered.
   */
  public static ExtensionList<DeclarativeAgentDescriptor> all() {
    return ExtensionList.lookup(DeclarativeAgentDescriptor.class);
  }

  /**
   * Get all {@link DeclarativeAgentDescriptor}s, sorted by name.
   *
   * @return a list of all {@link DeclarativeAgentDescriptor}s registered, sorted by name.
   */
  public static List<DeclarativeAgentDescriptor> allSorted() {
    return all().stream()
        .sorted(Comparator.comparing(DeclarativeAgentDescriptor::getName))
        .collect(Collectors.toList());
  }

  /**
   * Get a map of name-to-{@link DescribableModel} of all known/registered descriptors.
   *
   * @return A map of name-to-{@link DescribableModel}s
   */
  public static Map<String, DescribableModel> getDescribableModels() {
    Map<String, DescribableModel> models = new HashMap<>();

    for (DeclarativeAgentDescriptor d : all()) {
      for (String s : SymbolLookup.getSymbolValue(d)) {
        models.put(s, new DescribableModel<>(d.clazz));
      }
    }

    return models;
  }

  /**
   * Get the map of the subset of descriptors with no additional arguments - i.e., "none" and "any".
   *
   * @return A map of descriptors with no arguments.
   */
  public static Map<String, DescribableModel> zeroArgModels() {
    Map<String, DescribableModel> models = new HashMap<>();

    for (Map.Entry<String, DescribableModel> entry : getDescribableModels().entrySet()) {
      // All DeclarativeAgents will have at least one possible parameter, context.
      if (entry.getValue().getParameters().isEmpty()) {
        models.put(entry.getKey(), entry.getValue());
      }
    }

    return models;
  }

  /**
   * Get the map of the subset of descriptors with no required arguments.
   *
   * @return A map of descriptors with no required arguments.
   */
  public static Map<String, DescribableModel> noRequiredArgsModels() {
    Map<String, DescribableModel> models = new HashMap<>();

    for (Map.Entry<String, DescribableModel> entry : getDescribableModels().entrySet()) {
      if (entry.getValue().getFirstRequiredParameter() == null) {
        models.put(entry.getKey(), entry.getValue());
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
  public static @Nullable DeclarativeAgentDescriptor byName(@NonNull String name) {
    return (DeclarativeAgentDescriptor)
        SymbolLookup.get().findDescriptor(DeclarativeAgent.class, name);
  }

  /**
   * For a given name and map of arguments, find the corresponding descriptor and return an instance
   * using those arguments.
   *
   * @param name The name of the descriptor
   * @param arguments A map of arguments
   * @return The instantiated {@link DeclarativeAgent} instance, or null if the name isn't found.
   * @throws Exception if there are issues instantiating from the descriptor
   */
  public static @Nullable DeclarativeAgent<?> instanceForName(
      @NonNull String name, Map<String, Object> arguments) throws Exception {
    DeclarativeAgentDescriptor descriptor = byName(name);

    if (descriptor != null) {
      return instanceForDescriptor(descriptor, arguments);
    }

    return null;
  }

  /**
   * For a given descriptor and map of arguments, return an instance using those arguments.
   *
   * @param descriptor The descriptor instance
   * @param arguments A map of arguments
   * @return The instantiated {@link DeclarativeAgent} instance.
   * @throws Exception if there are issues instantiating from the descriptor
   */
  public static @NonNull DeclarativeAgent<?> instanceForDescriptor(
      @NonNull DeclarativeAgentDescriptor<?> descriptor, Map<String, Object> arguments)
      throws Exception {
    if (zeroArgModels().keySet().contains(descriptor.getName())
        || (noRequiredArgsModels().keySet().contains(descriptor.getName())
            && arguments.containsKey(UninstantiatedDescribable.ANONYMOUS_KEY)
            && arguments.size() == 1)) {
      return descriptor.newInstance();
    } else {
      return descriptor.newInstance(arguments);
    }
  }
}
