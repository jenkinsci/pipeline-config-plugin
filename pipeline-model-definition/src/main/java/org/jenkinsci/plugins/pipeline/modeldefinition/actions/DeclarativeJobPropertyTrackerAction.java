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

package org.jenkinsci.plugins.pipeline.modeldefinition.actions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import hudson.model.JobProperty;
import hudson.model.ParameterDefinition;
import hudson.triggers.Trigger;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption;

/**
 * Invisible action used for tracking what {@link JobProperty}s, {@link Trigger}s, and {@link
 * ParameterDefinition}s were defined in the Jenkinsfile for a given run.
 */
public class DeclarativeJobPropertyTrackerAction extends InvisibleAction {
  private final Set<String> jobProperties = new HashSet<>();
  private final Set<String> triggers = new HashSet<>();
  private final Set<String> parameters = new HashSet<>();
  private Set<String> options = new HashSet<>();

  @Deprecated
  public DeclarativeJobPropertyTrackerAction(
      @CheckForNull List<JobProperty> rawJobProperties,
      @CheckForNull List<Trigger> rawTriggers,
      @CheckForNull List<ParameterDefinition> rawParameters) {
    this(rawJobProperties, rawTriggers, rawParameters, null);
  }

  public DeclarativeJobPropertyTrackerAction(
      @CheckForNull List<JobProperty> rawJobProperties,
      @CheckForNull List<Trigger> rawTriggers,
      @CheckForNull List<ParameterDefinition> rawParameters,
      @CheckForNull List<DeclarativeOption> rawOptions) {
    if (rawJobProperties != null) {
      for (JobProperty p : rawJobProperties) {
        jobProperties.add(p.getDescriptor().getId());
      }
    }
    if (rawTriggers != null) {
      for (Trigger t : rawTriggers) {
        triggers.add(t.getDescriptor().getId());
      }
    }
    if (rawParameters != null) {
      for (ParameterDefinition d : rawParameters) {
        parameters.add(d.getName());
      }
    }
    if (rawOptions != null) {
      for (DeclarativeOption o : rawOptions) {
        options.add(o.getDescriptor().getName());
      }
    }
  }

  protected Object readResolve() throws IOException {
    if (this.options == null) {
      this.options = new HashSet<>();
    }

    return this;
  }

  /**
   * Alternative constructor for copying an existing {@link DeclarativeJobPropertyTrackerAction}'s
   * contents directly.
   *
   * @param copyFrom a non-null {@link DeclarativeJobPropertyTrackerAction}
   */
  public DeclarativeJobPropertyTrackerAction(
      @NonNull DeclarativeJobPropertyTrackerAction copyFrom) {
    this.jobProperties.addAll(copyFrom.getJobProperties());
    this.triggers.addAll(copyFrom.getTriggers());
    this.parameters.addAll(copyFrom.getParameters());
    this.options.addAll(copyFrom.getOptions());
  }

  public Set<String> getJobProperties() {
    return Collections.unmodifiableSet(jobProperties);
  }

  public Set<String> getTriggers() {
    return Collections.unmodifiableSet(triggers);
  }

  public Set<String> getParameters() {
    return Collections.unmodifiableSet(parameters);
  }

  public Set<String> getOptions() {
    return Collections.unmodifiableSet(options);
  }

  @Override
  public String toString() {
    return "DeclarativeJobPropertyTrackerAction[jobProperties:"
        + jobProperties
        + ",triggers:"
        + triggers
        + ",parameters:"
        + parameters
        + ",options:"
        + options
        + "]";
  }
}
