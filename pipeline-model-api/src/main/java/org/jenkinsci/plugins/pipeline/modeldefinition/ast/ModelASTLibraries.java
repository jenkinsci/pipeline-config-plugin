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

package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * A container for one or more library strings
 *
 * @author Andrew Bayer
 */
public final class ModelASTLibraries extends ModelASTElement implements ModelASTElementContainer {
  private List<ModelASTValue> libs = new ArrayList<>();

  public ModelASTLibraries(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return toJSONObject("libraries", libs);
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, libs);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder result = new StringBuilder("libraries {\n");
    for (ModelASTValue v : libs) {
      result.append("lib(").append(v.toGroovy()).append(")\n");
    }
    result.append("}\n");
    return result.toString();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(libs);
  }

  public boolean isEmpty() {
    return libs.isEmpty();
  }

  public List<ModelASTValue> getLibs() {
    return libs;
  }

  public void setLibs(List<ModelASTValue> libs) {
    this.libs = libs;
  }

  @Override
  public String toString() {
    return "ModelASTLibraries{" + "libs=" + libs + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    ModelASTLibraries that = (ModelASTLibraries) o;

    return getLibs() != null ? getLibs().equals(that.getLibs()) : that.getLibs() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getLibs() != null ? getLibs().hashCode() : 0);
    return result;
  }
}
