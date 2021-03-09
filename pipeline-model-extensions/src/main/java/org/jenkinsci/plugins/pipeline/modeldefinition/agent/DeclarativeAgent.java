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

package org.jenkinsci.plugins.pipeline.modeldefinition.agent;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import java.util.Map;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.withscript.WithScriptDescribable;
import org.jenkinsci.plugins.pipeline.modeldefinition.withscript.WithScriptScript;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsThread;

/**
 * Implementations for {@link DeclarativeAgentDescriptor} - pluggable agent backends for Declarative
 * Pipelines.
 *
 * @author Andrew Bayer
 */
public abstract class DeclarativeAgent<A extends DeclarativeAgent<A>>
    extends WithScriptDescribable<A> implements ExtensionPoint {
  protected boolean inStage;
  protected boolean doCheckout;
  protected String subdirectory;

  @Override
  public WithScriptScript getScript(CpsScript cpsScript) throws Exception {
    CpsThread c = CpsThread.current();
    if (c == null) throw new IllegalStateException("Expected to be called from CpsThread");

    cpsScript
        .getClass()
        .getClassLoader()
        .loadClass("org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript");

    return super.getScript(cpsScript);
  }

  public void setInStage(boolean inStage) {
    this.inStage = inStage;
  }

  public boolean isInStage() {
    return inStage;
  }

  public void initialize(Map<String, DeclarativeOption> options, boolean explicitAgentInStage) {}

  public boolean reuseRootAgent(Map<String, DeclarativeOption> options) {
    return false;
  }

  public void setDoCheckout(boolean doCheckout) {
    this.doCheckout = doCheckout;
  }

  public boolean isDoCheckout() {
    return doCheckout;
  }

  public void setSubdirectory(String subdirectory) {
    this.subdirectory = subdirectory;
  }

  public String getSubdirectory() {
    return subdirectory;
  }

  public void copyFlags(@NonNull DeclarativeAgent a) {
    setInStage(a.isInStage());
    setDoCheckout(a.isDoCheckout());
    setSubdirectory(a.getSubdirectory());
  }

  public boolean hasScmContext(CpsScript script) {
    try {
      script.getProperty("scm");
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public DeclarativeAgentDescriptor getDescriptor() {
    return (DeclarativeAgentDescriptor) super.getDescriptor();
  }
}
