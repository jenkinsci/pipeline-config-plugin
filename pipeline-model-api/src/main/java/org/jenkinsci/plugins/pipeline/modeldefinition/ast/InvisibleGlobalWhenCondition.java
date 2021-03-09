/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Special case of a {@link ModelASTWhenCondition} generated for a globally defined when condition.
 */
public class InvisibleGlobalWhenCondition extends ModelASTWhenCondition {
  private final String stageName;
  private final ModelASTStageBase stage;

  /**
   * Used to create invisible when conditions without base stage information, used for the "allOf"
   * conditional generated when a stage already has when conditions.
   */
  public InvisibleGlobalWhenCondition() {
    this(null, null);
  }

  /**
   * Used to create invisible when conditions with a base stage for comparison and querying.
   *
   * @param stageName The name of the stage this condition belongs to. Explicitly specified due to
   *     auto-generated stage names with matrices.
   * @param stage The {@link ModelASTStageBase} for the stage this condition belongs to, for
   *     inspection.
   */
  public InvisibleGlobalWhenCondition(String stageName, ModelASTStageBase stage) {
    super(null);
    this.stageName = stageName;
    this.stage = stage;
  }

  public String getStageName() {
    return stageName;
  }

  public ModelASTStageBase getStage() {
    return stage;
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return new JSONObject();
  }

  @Override
  @NonNull
  public String toGroovy() {
    return "";
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {}

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(stage);
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

    InvisibleGlobalWhenCondition that = (InvisibleGlobalWhenCondition) o;

    if (getStageName() != null
        ? !getStageName().equals(that.getStageName())
        : that.getStageName() != null) {
      return false;
    }
    return getStage() != null ? getStage().equals(that.getStage()) : that.getStage() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(getStage());
    result = 31 * result + Objects.hashCode(getStageName());
    return result;
  }
}
