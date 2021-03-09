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

package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Serializable;
import org.jenkinsci.plugins.pipeline.StageStatus;

/**
 * Used to mark why a stage was skipped for internal purposes, allowing us to abstract away handling
 * of skipped stages without needing chains of if/else or cases to get the different behaviors.
 */
public abstract class SkippedStageReason implements Serializable {
  protected String stageName;

  public SkippedStageReason(@NonNull String stageName) {
    this.stageName = stageName;
  }

  public boolean isNotExecutedNode() {
    return false;
  }

  @NonNull
  public abstract String getMessage();

  @NonNull
  public abstract String getStageStatus();

  @NonNull
  public abstract SkippedStageReason cloneWithNewStage(@NonNull String newStage);

  @NonNull
  public String getStageName() {
    return stageName;
  }

  public static class Failure extends SkippedStageReason {
    public Failure(@NonNull String stageName) {
      super(stageName);
    }

    @Override
    @NonNull
    public String getMessage() {
      return Messages.SkippedStageReason_FAILURE_Message(stageName);
    }

    @Override
    @NonNull
    public String getStageStatus() {
      return StageStatus.getSkippedForFailure();
    }

    @Override
    @NonNull
    public SkippedStageReason cloneWithNewStage(@NonNull String newStage) {
      return new Failure(newStage);
    }

    private static final long serialVersionUID = 1L;
  }

  public static class Unstable extends SkippedStageReason {
    public Unstable(@NonNull String stageName) {
      super(stageName);
    }

    @Override
    @NonNull
    public String getMessage() {
      return Messages.SkippedStageReason_UNSTABLE_Message(stageName);
    }

    @Override
    @NonNull
    public String getStageStatus() {
      return StageStatus.getSkippedForUnstable();
    }

    @Override
    @NonNull
    public SkippedStageReason cloneWithNewStage(@NonNull String newStage) {
      return new Unstable(newStage);
    }

    private static final long serialVersionUID = 1L;
  }

  public static class When extends SkippedStageReason {
    public When(@NonNull String stageName) {
      super(stageName);
    }

    @Override
    @NonNull
    public String getMessage() {
      return Messages.SkippedStageReason_WHEN_Message(stageName);
    }

    @Override
    @NonNull
    public String getStageStatus() {
      return StageStatus.getSkippedForConditional();
    }

    @Override
    @NonNull
    public SkippedStageReason cloneWithNewStage(@NonNull String newStage) {
      return new When(newStage);
    }

    private static final long serialVersionUID = 1L;
  }

  public static class Restart extends SkippedStageReason {
    private String restartedStage;

    public Restart(@NonNull String stageName, @NonNull String restartedStage) {
      super(stageName);
      this.restartedStage = restartedStage;
    }

    public String getRestartedStage() {
      return restartedStage;
    }

    @Override
    public boolean isNotExecutedNode() {
      return true;
    }

    @Override
    @NonNull
    public String getMessage() {
      return Messages.SkippedStageReason_RESTART_Message(stageName, restartedStage);
    }

    @Override
    @NonNull
    public String getStageStatus() {
      return StageStatus.getSkippedForRestart();
    }

    @Override
    @NonNull
    public SkippedStageReason cloneWithNewStage(@NonNull String newStage) {
      return new Restart(newStage, restartedStage);
    }

    private static final long serialVersionUID = 1L;
  }
}
