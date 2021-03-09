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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import static org.apache.commons.lang.StringUtils.isEmpty;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.util.ListBoxModel;
import java.io.IOException;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.Comparator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Stage condition based on the current branch. i.e. the env var BRANCH_NAME. As populated by {@link
 * jenkins.branch.BranchNameContributor}
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public class BranchConditional extends DeclarativeStageConditional<BranchConditional> {
  @Deprecated private transient String compare;
  private String pattern;
  private String comparator;

  @DataBoundConstructor
  public BranchConditional(String pattern) {
    this.pattern = pattern;
  }

  @Deprecated
  public String getCompare() {
    return compare;
  }

  public String getPattern() {
    return pattern;
  }

  protected Object readResolve() throws IOException {
    if (this.compare != null) {
      this.pattern = this.compare;
    }
    return this;
  }

  /**
   * The {@link Comparator} to use. Default is {@link Comparator#GLOB}
   *
   * @return the name of the comparator or null if default.
   */
  public String getComparator() {
    return comparator;
  }

  @DataBoundSetter
  public void setComparator(String comparator) {
    final Comparator c = Comparator.get(comparator, null);
    if (c != null) {
      this.comparator = c.name();
    } else {
      this.comparator = null;
    }
  }

  public boolean branchMatches(String toCompare, String actualBranch) {
    if (isEmpty(actualBranch) && isEmpty(toCompare)) {
      return true;
    } else if (isEmpty(actualBranch) || isEmpty(toCompare)) {
      return false;
    }

    Comparator c = Comparator.get(comparator, Comparator.GLOB);
    return c.compare(toCompare, actualBranch);
  }

  @Extension
  @Symbol("branch")
  public static class DescriptorImpl
      extends DeclarativeStageConditionalDescriptor<BranchConditional> {
    @Override
    @NonNull
    public String getDisplayName() {
      return "Execute the stage if the current branch matches a pattern";
    }

    @Override
    public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
      return ASTParserUtils.transformWhenContentToRuntimeAST(original);
    }

    public ListBoxModel doFillComparatorItems() {
      return Comparator.getSelectOptions(true, Comparator.GLOB);
    }
  }
}
