/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/** Stage condition based on object equality. */
public class EqualsConditional extends DeclarativeStageConditional<EqualsConditional> {
  private final Object expected;
  private final Object actual;

  @DataBoundConstructor
  public EqualsConditional(Object expected, Object actual) {
    this.expected = expected;
    this.actual = actual;
  }

  public Object getActual() {
    return actual;
  }

  public Object getExpected() {
    return expected;
  }

  @Extension
  @Symbol("equals")
  public static class DescriptorImpl
      extends DeclarativeStageConditionalDescriptor<EqualsConditional> {
    @Override
    @NonNull
    public String getDisplayName() {
      return "Execute the stage if two values are equal";
    }

    @Override
    public boolean inDirectiveGenerator() {
      // TODO: Figure out how to get Stapler to not barf on form->instance for a String as the value
      // but Object as the field type.
      return false;
    }

    @Override
    public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
      return ASTParserUtils.transformWhenContentToRuntimeAST(original);
    }
  }
}
