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

import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.EnvironmentNames.*;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.util.ListBoxModel;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.metadata.ContributorMetadataAction;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.Comparator;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.EnvironmentNames;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class ChangeRequestConditional
    extends DeclarativeStageConditional<ChangeRequestConditional> {

  private String id;
  private String target;
  private String branch;
  private String fork;
  private String url;
  private String title;
  private String author;
  private String authorDisplayName;
  private String authorEmail;
  private String comparator;

  @DataBoundConstructor
  public ChangeRequestConditional() {}

  /**
   * CHANGE_ID matching.
   *
   * @return id
   * @see ChangeRequestSCMHead#getId()
   */
  public String getId() {
    return id;
  }

  @DataBoundSetter
  public void setId(String id) {
    this.id = id;
  }

  /**
   * CHANGE_TARGET matching.
   *
   * @return target
   * @see SCMHead#getName()
   */
  public String getTarget() {
    return target;
  }

  @DataBoundSetter
  public void setTarget(String target) {
    this.target = target;
  }

  /**
   * CHANGE_BRANCH matching.
   *
   * @return target
   * @see ChangeRequestSCMHead2#getOriginName()
   */
  public String getBranch() {
    return branch;
  }

  @DataBoundSetter
  public void setBranch(String branch) {
    this.branch = branch;
  }

  /**
   * CHANGE_FORK matching.
   *
   * @return fork
   * @see SCMHeadOrigin.Fork#getName()
   */
  public String getFork() {
    return fork;
  }

  @DataBoundSetter
  public void setFork(String fork) {
    this.fork = fork;
  }

  /**
   * CHANGE_URL.
   *
   * <p>Ant style matching doesn't work on url strings due to it being meant for regular paths and
   * all those extra characters conflicts.
   *
   * @return url
   * @see ObjectMetadataAction#getObjectUrl()
   */
  public String getUrl() {
    return url;
  }

  @DataBoundSetter
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * CHANGE_TITLE matching.
   *
   * @return title
   * @see ObjectMetadataAction#getObjectDisplayName()
   */
  public String getTitle() {
    return title;
  }

  @DataBoundSetter
  public void setTitle(String title) {
    this.title = title;
  }

  /**
   * CHANGE_AUTHOR matching.
   *
   * @return authorDisplayName
   * @see ContributorMetadataAction#getContributor()
   */
  public String getAuthor() {
    return author;
  }

  @DataBoundSetter
  public void setAuthor(String author) {
    this.author = author;
  }

  /**
   * CHANGE_AUTHOR_DISPLAY_NAME matching.
   *
   * @return authorDisplayName
   * @see ContributorMetadataAction#getContributorDisplayName()
   */
  public String getAuthorDisplayName() {
    return authorDisplayName;
  }

  @DataBoundSetter
  public void setAuthorDisplayName(String authorDisplayName) {
    this.authorDisplayName = authorDisplayName;
  }

  /**
   * CHANGE_AUTHOR_EMAIL matching.
   *
   * @return authorEmail
   * @see ContributorMetadataAction#getContributorEmail()
   */
  public String getAuthorEmail() {
    return authorEmail;
  }

  @DataBoundSetter
  public void setAuthorEmail(String authorEmail) {
    this.authorEmail = authorEmail;
  }

  /**
   * The name of the {@link Comparator} to use. Default is {@link Comparator#EQUALS}
   *
   * @return the name of the comparator, or null if default.
   */
  public String getComparator() {
    return comparator;
  }

  @DataBoundSetter
  public void setComparator(String comparator) {
    Comparator c = Comparator.get(comparator, null);
    // TODO validation
    if (c != null) {
      this.comparator = c.name();
    } else {
      this.comparator = null;
    }
  }

  public boolean matches(EnvVars vars) {
    if (!CHANGE_ID.exists(vars)) {
      return false; // Not a change
    }
    Comparator c = Comparator.get(this.comparator, Comparator.EQUALS);
    try {
      check(id, c, CHANGE_ID, vars);
      check(target, c, CHANGE_TARGET, vars);
      check(branch, c, CHANGE_BRANCH, vars);
      check(fork, c, CHANGE_FORK, vars);
      check(title, c, CHANGE_TITLE, vars);
      check(author, c, CHANGE_AUTHOR, vars);
      check(authorDisplayName, c, CHANGE_AUTHOR_DISPLAY_NAME, vars);
      check(authorEmail, c, CHANGE_AUTHOR_EMAIL, vars);
      return true;
    } catch (AssertionError e) {
      return false;
    }
  }

  private void check(
      String pattern, Comparator comparator, EnvironmentNames varName, EnvVars vars) {
    if (isNotEmpty(pattern)) {
      if (!comparator.compare(pattern, varName.get(vars))) {
        throw new AssertionError(
            varName.name() + "(" + varName.get(vars) + ")" + " does not match " + pattern);
      }
    }
  }

  @Extension
  @Symbol("changeRequest")
  public static class DescriptorImpl
      extends DeclarativeStageConditionalDescriptor<ChangeRequestConditional> {
    @Override
    public String getDisplayName() {
      return "Execute the stage if the build is on a change request";
    }

    @Override
    public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
      return ASTParserUtils.transformWhenContentToRuntimeAST(original);
    }

    public ListBoxModel doFillComparatorItems() {
      return Comparator.getSelectOptions(true, Comparator.EQUALS);
    }
  }
}
