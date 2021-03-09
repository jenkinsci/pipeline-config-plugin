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

package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/** If {@link ModelASTStage} will be executed or not. */
public class ModelASTWhen extends ModelASTElement {

  private List<ModelASTWhenContent> conditions = new ArrayList<>();

  private Boolean beforeAgent;

  private Boolean beforeInput;

  private Boolean beforeOptions;

  public ModelASTWhen(Object sourceLocation) {
    super(sourceLocation);
  }

  public List<ModelASTWhenContent> getConditions() {
    return conditions;
  }

  public void setConditions(List<ModelASTWhenContent> conditions) {
    this.conditions = conditions;
  }

  public Boolean getBeforeAgent() {
    return beforeAgent;
  }

  public void setBeforeAgent(Boolean beforeAgent) {
    this.beforeAgent = beforeAgent;
  }

  public Boolean getBeforeInput() {
    return beforeInput;
  }

  public void setBeforeInput(Boolean beforeInput) {
    this.beforeInput = beforeInput;
  }

  public Boolean getBeforeOptions() {
    return beforeOptions;
  }

  public void setBeforeOptions(Boolean beforeOptions) {
    this.beforeOptions = beforeOptions;
  }

  @Override
  @NonNull
  public Object toJSON() {
    return new JSONObject()
        .accumulate("conditions", toJSONArray(conditions))
        .elementOpt("beforeAgent", beforeAgent)
        .elementOpt("beforeInput", beforeInput)
        .elementOpt("beforeOptions", beforeOptions);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder result = new StringBuilder("when {\n");
    if (beforeAgent != null && beforeAgent) {
      result.append("beforeAgent true\n");
    }
    if (beforeInput != null && beforeInput) {
      result.append("beforeInput true\n");
    }
    if (beforeOptions != null && beforeOptions) {
      result.append("beforeOptions true\n");
    }
    result.append(toGroovy(conditions));
    result.append("}\n");
    return result.toString();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(conditions);
  }

  @Override
  public String toString() {
    return "ModelASTWhen{"
        + "conditions="
        + conditions
        + ", beforeAgent="
        + beforeAgent
        + ", beforeInput="
        + beforeInput
        + ", beforeOptions="
        + beforeOptions
        + "}";
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, conditions);
  }
}
