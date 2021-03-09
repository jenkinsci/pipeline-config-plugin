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
package org.jenkinsci.plugins.pipeline.modeldefinition;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

/**
 * Loads the main "pipeline" step as well as the additional CPS-transformed code it depends on.
 *
 * @author Andrew Bayer
 */
@Extension
public class ModelStepLoader extends GlobalVariable {
  public static final String STEP_NAME = "pipeline";

  @Override
  @NonNull
  public String getName() {
    return STEP_NAME;
  }

  @Override
  @NonNull
  public Object getValue(@NonNull CpsScript script) throws Exception {
    CpsThread c = CpsThread.current();
    if (c == null) throw new IllegalStateException("Expected to be called from CpsThread");

    return script
        .getClass()
        .getClassLoader()
        .loadClass("org.jenkinsci.plugins.pipeline.modeldefinition.ModelInterpreter")
        .getConstructor(CpsScript.class)
        .newInstance(script);
  }

  /**
   * Make sure we've invalidated the option type caches due to potential race conditions with their
   * population. Because we're using {@link Initializer}, we need this to be triggered in an {@link
   * Extension}, so here is as good a place as any.
   */
  @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
  public static void invalidateOptionTypeCaches() {
    Options.invalidateCaches();
  }
}
