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
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * {@code when} container generated when adding invisible global {@code when} conditions to a stage,
 * containing the new invisible conditions and any explicitly defined ones. When created with
 * existing conditions, the existing {@code when} container is stored for use as well. This is used
 * as a marker to avoid validation, JSON/Groovy generation, etc for the generated container.
 */
public class InvisibleWhen extends ModelASTWhen {
  /** The optional original when container on the stage, used for delegation. */
  private ModelASTWhen originalWhen;

  public InvisibleWhen() {
    super(null);
  }

  public void setOriginalWhen(ModelASTWhen originalWhen) {
    this.originalWhen = originalWhen;
  }

  @Override
  public Object getSourceLocation() {
    return originalWhen != null ? originalWhen.getSourceLocation() : null;
  }

  @Override
  public Boolean getBeforeAgent() {
    return originalWhen != null && originalWhen.getBeforeAgent() != null
        ? originalWhen.getBeforeAgent()
        : false;
  }

  @Override
  public Boolean getBeforeInput() {
    return originalWhen != null && originalWhen.getBeforeInput() != null
        ? originalWhen.getBeforeInput()
        : false;
  }

  @Override
  public Boolean getBeforeOptions() {
    return originalWhen != null && originalWhen.getBeforeOptions() != null
        ? originalWhen.getBeforeOptions()
        : false;
  }

  @NonNull
  @Override
  public Object toJSON() {
    return originalWhen != null ? originalWhen.toJSON() : new JSONObject();
  }

  @NonNull
  @Override
  public String toGroovy() {
    return originalWhen != null ? originalWhen.toGroovy() : "";
  }

  @Override
  public void removeSourceLocation() {
    if (originalWhen != null) {
      originalWhen.removeSourceLocation();
    }
  }

  @Override
  public String toString() {
    return originalWhen != null ? originalWhen.toString() : super.toString();
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    if (originalWhen != null) {
      originalWhen.validate(validator);
    }
  }
}
