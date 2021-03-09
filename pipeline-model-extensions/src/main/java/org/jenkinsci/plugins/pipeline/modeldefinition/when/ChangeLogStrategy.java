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

package org.jenkinsci.plugins.pipeline.modeldefinition.when;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.scm.api.SCMHead;

/**
 * Extension point for what strategy to use when examining the changelog.
 *
 * <p>In particular if a given {@link jenkins.scm.api.SCMHead} is a change request that suggest all
 * builds should be examined.
 */
public class ChangeLogStrategy implements ExtensionPoint {

  /**
   * @param head the head in question
   * @return {@code true} if all builds changelogs should be examined.
   */
  protected boolean shouldExamineAllBuilds(@NonNull SCMHead head) {
    return false;
  }

  public static boolean isExamineAllBuilds(@NonNull SCMHead head) {
    for (ChangeLogStrategy s : ExtensionList.lookup(ChangeLogStrategy.class)) {
      if (s.shouldExamineAllBuilds(head)) {
        return true;
      }
    }
    return false;
  }
}
