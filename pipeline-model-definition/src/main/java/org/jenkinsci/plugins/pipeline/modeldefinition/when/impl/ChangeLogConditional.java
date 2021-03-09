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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.regex.Pattern;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Conditional that checks the messages in the changelog.
 *
 * <p>The build must first have collected the changelog via for example <code>checkout scm</code>.
 */
public class ChangeLogConditional extends DeclarativeStageConditional<ChangeLogConditional> {

  private Pattern asPattern;
  private Pattern multiLinePattern;
  private String pattern;

  @DataBoundConstructor
  public ChangeLogConditional(String pattern) {
    this.pattern = pattern;
    this.asPattern = Pattern.compile(pattern);
    this.multiLinePattern =
        Pattern.compile(expandForMultiLine(pattern), Pattern.MULTILINE | Pattern.DOTALL);
  }

  public Pattern getAsPattern() {
    return asPattern;
  }

  public String getPattern() {
    return pattern;
  }

  public Pattern getMultiLinePattern() {
    return multiLinePattern;
  }

  @Extension
  @Symbol("changelog")
  public static class DescriptorImpl
      extends DeclarativeStageConditionalDescriptor<ChangeLogConditional> {
    @Override
    @NonNull
    public String getDisplayName() {
      return "Execute the stage if a commit message in the changelog matches";
    }

    @Override
    public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
      return ASTParserUtils.transformWhenContentToRuntimeAST(original);
    }
  }

  @Restricted(NoExternalUse.class)
  public static String expandForMultiLine(String pattern) {
    return "(?m)(?s)^[^\\r\\n]*?" + pattern + "[^\\r\\n]*?$";
  }
}
