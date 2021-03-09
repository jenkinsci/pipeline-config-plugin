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
package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.codehaus.groovy.ast.ASTNode;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

public abstract class ModelASTElement implements ModelASTMarkerInterface {
  /**
   * The sourceLocation is a reference to whatever section of the original source we're parsed from
   * corresponds to this element. When parsed from Pipeline Script, it's an {@link ASTNode}, and
   * when parsed from JSON, it's a {@link JSONObject}.
   */
  private Object sourceLocation;

  ModelASTElement(Object sourceLocation) {
    this.sourceLocation = sourceLocation;
  }

  public Object getSourceLocation() {
    return sourceLocation;
  }

  public void setSourceLocation(Object sourceLocation) {
    this.sourceLocation = sourceLocation;
  }

  /**
   * Translates this element and any children it may have into JSON conforming to the schema.
   *
   * @return Generally a {@link JSONObject} or {@link JSONArray} but for some leaf nodes, may be a
   *     {@link String} or other simple class.
   */
  @NonNull
  public abstract Object toJSON();

  @CheckForNull
  protected static Object toJSON(@CheckForNull ModelASTMarkerInterface item) {
    return item != null ? item.toJSON() : null;
  }

  @CheckForNull
  protected static Object toJSONCheckEmpty(@CheckForNull ModelASTElementContainer item) {
    return item != null && !item.isEmpty() ? item.toJSON() : null;
  }

  @NonNull
  protected static <T extends ModelASTMarkerInterface> JSONArray toJSONArray(
      @CheckForNull Collection<T> list) {
    JSONArray a = new JSONArray();
    if (list != null) {
      for (T item : list) {
        a.add(toJSON(item));
      }
    }
    return a;
  }

  @NonNull
  protected static <K extends ModelASTMarkerInterface, V extends ModelASTMarkerInterface>
      JSONArray toJSONArray(@CheckForNull Map<K, V> map) {
    final JSONArray a = new JSONArray();
    if (map != null) {
      for (Map.Entry<K, V> entry : map.entrySet()) {
        JSONObject o = new JSONObject();
        o.accumulate("key", entry.getKey().toJSON());
        o.accumulate("value", entry.getValue().toJSON());
        a.add(o);
      }
    }
    return a;
  }

  @NonNull
  protected static <T extends ModelASTMarkerInterface> JSONObject toJSONObject(
      @NonNull String key, @CheckForNull Collection<T> list) {
    return new JSONObject().accumulate(key, toJSONArray(list));
  }

  @CheckForNull
  protected static <T extends Collection> T nullIfEmpty(@CheckForNull T list) {
    return list == null || list.isEmpty() ? null : list;
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  public abstract String toGroovy();

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static String toGroovy(@CheckForNull ModelASTMarkerInterface item) {
    return item != null ? item.toGroovy() : "";
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static String toGroovyCheckEmpty(@CheckForNull ModelASTElementContainer item) {
    return item != null && !item.isEmpty() ? item.toGroovy() : "";
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static <T extends ModelASTMarkerInterface> String toGroovy(List<T> list) {
    StringBuilder result = new StringBuilder();
    for (T item : list) {
      result.append(item.toGroovy()).append("\n");
    }
    return result.toString();
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static <T extends ModelASTMarkerInterface> String toGroovyArgList(Collection<T> list) {
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for (T item : list) {
      if (first) {
        first = false;
      } else {
        result.append(", ");
      }
      result.append(item.toGroovy());
    }
    return result.toString();
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static <K extends ModelASTMarkerInterface, V extends ModelASTMarkerInterface>
      String toGroovyArgList(Map<K, V> map, String separator) {
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for (Map.Entry<K, V> entry : map.entrySet()) {
      if (first) {
        first = false;
      } else {
        result.append(", ");
      }
      result
          .append(entry.getKey().toGroovy())
          .append(separator)
          .append(entry.getValue().toGroovy());
    }
    return result.toString();
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static String toGroovyBlock(String name, ModelASTMarkerInterface item) {
    StringBuilder result = new StringBuilder();
    if (name != null) {
      result.append(name).append(" ");
    }
    result.append("{\n");
    result.append(toGroovy(item));
    result.append("}\n");
    return result.toString();
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static <T extends ModelASTMarkerInterface> String toGroovyBlock(
      String name, List<T> list) {
    StringBuilder result = new StringBuilder();
    if (name != null) {
      result.append(name).append(" ");
    }
    result.append("{\n");
    result.append(toGroovy(list));
    result.append("}\n");
    return result.toString();
  }

  /**
   * Translates this element and any children it may have into Pipeline Config-formatted Groovy,
   * without any indentations.
   *
   * @return A simple {@link String} of Groovy code for this element and its children.
   */
  @NonNull
  protected static <K extends ModelASTMarkerInterface, V extends ModelASTMarkerInterface>
      String toGroovyBlock(String name, Map<K, V> map, String separator) {
    StringBuilder result = new StringBuilder();
    if (name != null) {
      result.append(name).append(" ");
    }
    result.append("{\n");
    for (Map.Entry<K, V> entry : map.entrySet()) {
      result
          .append(entry.getKey().toGroovy())
          .append(separator)
          .append(entry.getValue().toGroovy())
          .append('\n');
    }
    result.append("}\n");
    return result.toString();
  }

  /**
   * Called to do whatever validation is necessary for this element. Overridden in most cases.
   *
   * @param validator A {@link ModelValidator} to use for more complicated validation.
   */
  public void validate(@NonNull ModelValidator validator) {
    // No-op
  }

  protected static void validate(
      @NonNull ModelValidator validator, @CheckForNull ModelASTMarkerInterface... items) {
    if (items != null && items.length > 0) {
      validate(validator, Arrays.asList(items));
    }
  }

  protected static <T extends ModelASTMarkerInterface> void validate(
      @NonNull ModelValidator validator,
      @CheckForNull List<T> list,
      @CheckForNull ModelASTMarkerInterface... items) {
    validate(validator, items);
    if (list != null) {
      for (T item : list) {
        if (item != null) {
          item.validate(validator);
        }
      }
    }
  }

  protected static <K extends ModelASTMarkerInterface, V extends ModelASTMarkerInterface>
      void validate(
          @NonNull ModelValidator validator,
          @CheckForNull Map<K, V> map,
          @CheckForNull ModelASTMarkerInterface... items) {
    validate(validator, items);
    if (map != null) {
      for (Map.Entry<K, V> entry : map.entrySet()) {
        entry.getKey().validate(validator);
        entry.getValue().validate(validator);
      }
    }
  }

  /** Removes the source location value from this element. */
  public void removeSourceLocation() {
    sourceLocation = null;
  }

  /** Removes the source location value from this list of elements element. */
  protected static void removeSourceLocationsFrom(@CheckForNull ModelASTMarkerInterface... items) {
    if (items != null) {
      removeSourceLocationsFrom(Arrays.asList(items));
    }
  }

  /** Removes the source location value from this list of elements. */
  protected static <T extends ModelASTMarkerInterface> void removeSourceLocationsFrom(
      @CheckForNull Collection<T> list) {
    if (list != null) {
      for (T item : list) {
        if (item != null) {
          item.removeSourceLocation();
        }
      }
    }
  }

  /** Removes the source location value from this list of elements. */
  protected static <T extends ModelASTMarkerInterface> void removeSourceLocationsFrom(
      @CheckForNull Collection<T> list, @CheckForNull ModelASTMarkerInterface... items) {
    removeSourceLocationsFrom(items);
    removeSourceLocationsFrom(list);
  }

  /** Removes the source location value from this map of elements. */
  protected static <K extends ModelASTMarkerInterface, V extends ModelASTMarkerInterface>
      void removeSourceLocationsFrom(
          @CheckForNull Map<K, V> map, @CheckForNull ModelASTMarkerInterface... items) {
    if (map != null) {
      removeSourceLocationsFrom(items);
      for (Map.Entry<K, V> entry : map.entrySet()) {
        entry.getKey().removeSourceLocation();
        entry.getValue().removeSourceLocation();
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "ModelASTElement{}";
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return ModelASTElement.class.hashCode();
  }
}
