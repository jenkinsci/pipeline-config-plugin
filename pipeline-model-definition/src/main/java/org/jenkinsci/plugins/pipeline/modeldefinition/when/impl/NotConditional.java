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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.List;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/** Inverted match of a stage condition */
public class NotConditional extends DeclarativeStageConditional<NotConditional> {
  private DeclarativeStageConditional<? extends DeclarativeStageConditional> child;

  @DataBoundConstructor
  public NotConditional(DeclarativeStageConditional<? extends DeclarativeStageConditional> child) {
    this.child = child;
  }

  public DeclarativeStageConditional<? extends DeclarativeStageConditional> getChild() {
    return child;
  }

  @Override
  @NonNull
  public List<DeclarativeStageConditional<? extends DeclarativeStageConditional>> getChildren() {
    return ImmutableList.of(child);
  }

  @Extension
  @Symbol("not")
  public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<NotConditional> {
    @Override
    @NonNull
    public String getDisplayName() {
      return "Execute the stage if the nested condition is false";
    }

    @Override
    public int getAllowedChildrenCount() {
      return 1;
    }

    @Override
    public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
      return ASTParserUtils.transformWhenContentToRuntimeAST(original);
    }
  }
}
