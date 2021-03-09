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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.Comparator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class TagConditional extends DeclarativeStageConditional<TagConditional> {
  private final String pattern;
  private String comparator;

  @DataBoundConstructor
  public TagConditional(String pattern) {
    this.pattern = pattern;
  }

  public String getPattern() {
    return pattern;
  }

  /**
   * The {@link Comparator} to use.
   *
   * @return the name of the comparator or null if default.
   */
  public String getComparator() {
    return comparator;
  }

  @DataBoundSetter
  public void setComparator(String comparator) {
    final Comparator c = Comparator.get(comparator, null);
    // TODO validation
    if (c != null) {
      this.comparator = c.name();
    } else {
      this.comparator = null;
    }
  }

  public boolean tagMatches(String actualTag) {
    if (StringUtils.isEmpty(actualTag)) {
      return false; // This is no tag build.
    }
    if (StringUtils.isEmpty(pattern) && StringUtils.isNotEmpty(actualTag)) {
      return true; // This is a tag build and user doesn't care what the name is
    }

    Comparator c = Comparator.get(comparator, Comparator.GLOB);

    return c.compare(pattern, actualTag);
  }

  @Extension
  @Symbol("tag")
  public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<TagConditional> {
    @Override
    public String getDisplayName() {
      return "Execute this stage if the build is running against an SCM tag matching the given pattern";
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
