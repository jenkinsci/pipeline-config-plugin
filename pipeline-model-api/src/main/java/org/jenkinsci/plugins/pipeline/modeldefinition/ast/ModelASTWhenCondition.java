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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/** @author Andrew Bayer */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public class ModelASTWhenCondition extends ModelASTElement implements ModelASTWhenContent {
  private String name;
  private ModelASTArgumentList args;
  private List<ModelASTWhenContent> children = new ArrayList<>();

  public ModelASTWhenCondition(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return new JSONObject()
        .accumulate("name", name)
        .elementOpt("arguments", toJSON(args))
        .elementOpt("children", nullIfEmpty(toJSONArray(children)));
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, children, args);
  }

  @Override
  @NonNull
  public String toGroovy() {

    StringBuilder result = new StringBuilder();
    if (!children.isEmpty()) {
      result.append(toGroovyBlock(name, children));
    } else {
      result.append(name).append(" ").append(getArgs().toGroovy());
    }
    return result.toString();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(children, args);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ModelASTArgumentList getArgs() {
    return args;
  }

  public void setArgs(ModelASTArgumentList args) {
    this.args = args;
  }

  public List<ModelASTWhenContent> getChildren() {
    return children;
  }

  public void setChildren(List<ModelASTWhenContent> c) {
    this.children = c;
  }

  @Override
  public String toString() {
    return "ModelASTWhenCondition{"
        + "name='"
        + name
        + '\''
        + ", args="
        + args
        + ", children="
        + children
        + "}";
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

    ModelASTWhenCondition that = (ModelASTWhenCondition) o;

    if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
      return false;
    }
    if (getChildren() != null
        ? !getChildren().equals(that.getChildren())
        : that.getChildren() != null) {
      return false;
    }
    return getArgs() != null ? getArgs().equals(that.getArgs()) : that.getArgs() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getName() != null ? getName().hashCode() : 0);
    result = 31 * result + (getArgs() != null ? getArgs().hashCode() : 0);
    result = 31 * result + (getChildren() != null ? getChildren().hashCode() : 0);
    return result;
  }
}
