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

package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class Label extends DeclarativeAgent<Label> {
  private String label;
  private String customWorkspace;

  @DataBoundConstructor
  public Label(String label) {
    // Label *can* be null. That's fine.
    this.label = label;
  }

  public @Nullable String getLabel() {
    return label;
  }

  public @CheckForNull String getCustomWorkspace() {
    return customWorkspace;
  }

  @DataBoundSetter
  public void setCustomWorkspace(String customWorkspace) {
    this.customWorkspace = customWorkspace;
  }

  @Extension(ordinal = -800)
  @Symbol({"label", "node"})
  public static class DescriptorImpl extends DeclarativeAgentDescriptor<Label> {
    @Override
    @NonNull
    public String getDisplayName() {
      return "Run on an agent matching a label";
    }

    public FormValidation doCheckLabel(@QueryParameter String label) {
      if (StringUtils.isEmpty(Util.fixEmptyAndTrim(label))) {
        return FormValidation.error("Label is required.");
      } else {
        return FormValidation.ok();
      }
    }
  }
}
