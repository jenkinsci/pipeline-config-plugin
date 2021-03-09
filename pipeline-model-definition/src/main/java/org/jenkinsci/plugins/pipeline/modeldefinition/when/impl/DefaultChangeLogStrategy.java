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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.ChangeLogStrategy;

@Extension
public class DefaultChangeLogStrategy extends ChangeLogStrategy {

  private Class<?> bitbucketPr;
  private Class<?> githubPr;

  public DefaultChangeLogStrategy() {
    try {
      githubPr = Class.forName("org.jenkinsci.plugins.github_branch_source.PullRequestSCMHead");
    } catch (ClassNotFoundException e) {
      githubPr = null;
    }
    try {
      bitbucketPr = Class.forName("com.cloudbees.jenkins.plugins.bitbucket.PullRequestSCMHead");
    } catch (ClassNotFoundException e) {
      bitbucketPr = null;
    }
  }

  @Override
  protected boolean shouldExamineAllBuilds(@NonNull SCMHead head) {
    if (githubPr != null && head.getClass().isAssignableFrom(githubPr)) {
      return true;
    }
    if (bitbucketPr != null && head.getClass().isAssignableFrom(bitbucketPr)) {
      return true;
    }
    return false;
  }
}
