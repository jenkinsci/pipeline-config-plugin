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

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents the value in a key/value pair, as used in {@link ModelASTEnvironment}, {@link
 * ModelASTNamedArgumentList} and elsewhere.
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public abstract class ModelASTValue extends ModelASTElement
    implements ModelASTMethodArg, ModelASTEnvironmentValue {
  /* package */ ModelASTValue(Object sourceLocation, Object v) {
    super(sourceLocation);
    this.value = v;
  }

  private Object value;

  /**
   * If the value can be determined without side-effect at AST parsing time, this method returns
   * true, and {@link #getValue()} returns its value.
   *
   * @return {@code true} if the value can be determined without side-effects at AST parsing time.
   */
  public abstract boolean isLiteral();

  /**
   * Returns a value or an expression that represents this value.
   *
   * <p>This model is used at the compile time, so it's not always possible to obtain the actual
   * value. Imagine something like {@code secret('12345')} or even {@code pow(2,10)}.
   *
   * <p>In case the value is an expression, this method returns a string represntation suitable for
   * the editor.
   *
   * <p>For example, if the value is {@code foobar(x)}, we want the editor to show "${foobar(x)}"
   *
   * @return returens the value or an expression that represents this value.
   */
  public Object getValue() {
    return value;
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return new JSONObject().accumulate("isLiteral", isLiteral()).accumulate("value", getValue());
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

    ModelASTValue that = (ModelASTValue) o;

    return getValue() != null ? getValue().equals(that.getValue()) : that.getValue() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getValue() != null ? getValue().hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ModelASTValue{" + "value=" + value + ", isLiteral=" + isLiteral() + '}';
  }

  public static ModelASTValue fromConstant(final Object o, Object sourceLocation) {
    return new ConstantValue(sourceLocation, o);
  }

  public static ModelASTValue fromGString(final String gstring, Object sourceLocation) {
    return new GStringValue(sourceLocation, gstring);
  }

  private static final class ConstantValue extends ModelASTValue {
    ConstantValue(Object sourceLocation, Object v) {
      super(sourceLocation, v);
    }

    @Override
    public boolean isLiteral() {
      return true;
    }

    @Override
    @NonNull
    public String toGroovy() {
      if (getValue() instanceof String) {
        String str = (String) getValue();
        str = str.replace("\\", "\\\\");
        if (str.indexOf('\n') == -1) {
          return "'" + (str.replace("'", "\\'")) + "'";
        } else {
          return "'''" + (str.replace("'", "\\'")) + "'''";
        }
      } else if (getValue() != null) {
        return getValue().toString();
      } else {
        return "null";
      }
    }
  }

  private static final class GStringValue extends ModelASTValue {
    GStringValue(Object sourceLocation, Object v) {
      super(sourceLocation, v);
    }

    @Override
    public boolean isLiteral() {
      return false;
    }

    @Override
    @NonNull
    public String toGroovy() {
      String gstring = (String) getValue();
      if (gstring.startsWith("${") && gstring.endsWith("}")) {
        return gstring.substring(2, gstring.length() - 1);
      } else {
        return gstring;
      }
    }
  }
}
