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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Closure;
import hudson.Extension;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenExpression;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.BlockStatementMatch;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Stage condition based on the current branch. i.e. the env var BRANCH_NAME. As populated by {@link
 * jenkins.branch.BranchNameContributor}
 */
public class ExpressionConditional extends DeclarativeStageConditional<ExpressionConditional> {
  private final String block;

  // Needs to be transient to avoid potential excessive pickling - see JENKINS-48209
  private transient Closure closureBlock;

  @Deprecated
  public ExpressionConditional(String block) {
    this.block = block;
    this.closureBlock = null;
  }

  @DataBoundConstructor
  public ExpressionConditional() {
    this.block = null;
  }

  public String getBlock() {
    return block;
  }

  public Closure getClosureBlock() {
    return closureBlock;
  }

  public void setClosureBlock(Closure closureBlock) {
    this.closureBlock = closureBlock;
  }

  @Extension
  @Symbol("expression")
  public static class DescriptorImpl
      extends DeclarativeStageConditionalDescriptor<ExpressionConditional> {
    @Override
    @NonNull
    public String getDisplayName() {
      return "Execute the stage if a Scripted Pipeline expression evaluates as true";
    }

    /** ExpressionConditional has no form equivalent without jumping through some hoops, so... */
    @Override
    public boolean inDirectiveGenerator() {
      return false;
    }

    @Override
    public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
      if (original != null && original instanceof ModelASTWhenExpression) {
        ModelASTWhenExpression whenExpr = (ModelASTWhenExpression) original;
        if (whenExpr.getSourceLocation() instanceof Statement) {
          BlockStatementMatch block =
              ASTParserUtils.matchBlockStatement((Statement) whenExpr.getSourceLocation());
          if (block != null) {
            return GeneralUtils.callX(
                ClassHelper.make(DescriptorImpl.class),
                "instanceFromClosure",
                new ArgumentListExpression(block.getBody()));
          }
        }
      }

      return GeneralUtils.constX(null);
    }

    @Whitelisted
    public static ExpressionConditional instanceFromClosure(Closure c) {
      ExpressionConditional conditional = new ExpressionConditional();
      conditional.setClosureBlock(c);
      return conditional;
    }
  }

  private static final long serialVersionUID = 1L;
}
