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
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.causes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Cause;
import hudson.model.Run;

public class RestartDeclarativePipelineCause extends Cause {
  private int originRunNumber;
  private String originStage;
  private transient Run<?, ?> run;

  public RestartDeclarativePipelineCause(@NonNull Run<?, ?> original, @NonNull String originStage) {
    this.originRunNumber = original.getNumber();
    this.originStage = originStage;
  }

  @Override
  public void onAddedTo(Run run) {
    super.onAddedTo(run);
    this.run = run;
  }

  @Override
  public void onLoad(Run<?, ?> run) {
    super.onLoad(run);
    this.run = run;
  }

  public int getOriginRunNumber() {
    return originRunNumber;
  }

  @NonNull
  public String getOriginStage() {
    return originStage;
  }

  @CheckForNull
  public Run<?, ?> getOriginal() {
    return run.getParent().getBuildByNumber(originRunNumber);
  }

  @Override
  public String getShortDescription() {
    return Messages.RestartedDeclarativePipelineCause_ShortDescription(
        originRunNumber, originStage);
  }
}
