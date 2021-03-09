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
import org.apache.commons.lang.StringUtils;

/**
 * Represents the special step which are executed without validation against the declarative subset.
 *
 * @see ModelASTScriptBlock
 * @see ModelASTWhenExpression
 */
public abstract class AbstractModelASTCodeBlock extends ModelASTStep {

  protected AbstractModelASTCodeBlock(Object sourceLocation, String name) {
    super(sourceLocation);
    this.setName(name);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder result = new StringBuilder(getName()).append(" {\n");
    result.append(codeBlockAsString());
    result.append("\n}\n");
    return result.toString();
  }

  public String codeBlockAsString() {
    if (getArgs() == null) {
      return null;
    } else if (isLiteralSingleArg()) {
      Object v = getSingleValue().getValue();
      if (v instanceof String) {
        List<String> retList = new ArrayList<>();
        for (String s : v.toString().split("\\r?\\n")) {
          retList.add(s.trim());
        }
        return StringUtils.join(retList, "\n");
      } else {
        return v.toString();
      }
    } else {
      return getArgs().toGroovy();
    }
  }

  protected ModelASTValue getSingleValue() {
    if (getArgs() instanceof ModelASTSingleArgument) {
      return ((ModelASTSingleArgument) getArgs()).getValue();
    } else if (getArgs() instanceof ModelASTNamedArgumentList) {
      ModelASTNamedArgumentList namedArgs = (ModelASTNamedArgumentList) getArgs();
      if (namedArgs.getArguments().size() == 1 && namedArgs.containsKeyName("scriptBlock")) {
        return namedArgs.valueForName("scriptBlock");
      }
    }
    return null;
  }

  protected boolean isLiteralSingleArg() {
    return getArgs() != null && getSingleValue() != null && getSingleValue().isLiteral();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AbstractModelASTCodeBlock that = (AbstractModelASTCodeBlock) o;

    if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
      return false;
    }
    if (isLiteralSingleArg() && that.isLiteralSingleArg()) {
      return codeBlockAsString().equals(that.codeBlockAsString());
    } else {
      return getArgs() != null ? getArgs().equals(that.getArgs()) : that.getArgs() == null;
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "{"
        + "name='"
        + getName()
        + '\''
        + ", args="
        + getArgs()
        + "}";
  }
}
