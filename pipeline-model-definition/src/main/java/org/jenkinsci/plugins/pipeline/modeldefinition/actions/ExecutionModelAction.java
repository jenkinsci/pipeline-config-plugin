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

package org.jenkinsci.plugins.pipeline.modeldefinition.actions;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.InvisibleAction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;

public class ExecutionModelAction extends InvisibleAction {
  private String stagesUUID;
  private List<ModelASTPipelineDef> pipelineDefs = new ArrayList<>();

  /** Only present for backwards compatibility during deserialization, null in all other cases. */
  @Deprecated private @CheckForNull ModelASTStages stages;
  /** Only present for backwards compatibility during deserialization, null in all other cases. */
  @Deprecated private @CheckForNull List<ModelASTStages> stagesList;

  public ExecutionModelAction(ModelASTPipelineDef pipeline) {
    this.pipelineDefs.add(pipeline);
  }

  /** @deprecated Use {@link #ExecutionModelAction(ModelASTPipelineDef)} instead. */
  @Deprecated
  public ExecutionModelAction(ModelASTStages s) {
    this(createDummyPipelineDef(s));
  }

  /**
   * @deprecated Use {@link #ExecutionModelAction(ModelASTPipelineDef)} and {@link #addPipelineDef}
   *     instead.
   */
  @Deprecated
  public ExecutionModelAction(List<ModelASTStages> stages) {
    for (ModelASTStages s : stages) {
      pipelineDefs.add(createDummyPipelineDef(s));
    }
  }

  protected Object readResolve() throws IOException {
    // Originally, `stages` was the only field in this class. `stagesList` was added to support
    // Pipelines that use
    // Declarative more than once. If `stages` is non-null, `stagesList` is null, and vice-versa.
    // For instances
    // created after `pipelinedefs` was added, both `stagesList` and `stages` will be null.
    if (pipelineDefs == null) {
      pipelineDefs = new ArrayList<>();
    }
    if (stages != null) {
      pipelineDefs.add(createDummyPipelineDef(stages));
      stages = null;
    } else if (stagesList != null) {
      for (ModelASTStages s : stagesList) {
        pipelineDefs.add(createDummyPipelineDef(s));
      }
      stagesList = null;
    }
    return this;
  }

  /**
   * Create an {@link ModelASTPipelineDef} from a {@link ModelASTStages} object.
   *
   * <p>Only used for backwards compatibility in cases where we do not have the full {@link
   * ModelASTPipelineDef}.
   */
  private static ModelASTPipelineDef createDummyPipelineDef(ModelASTStages s) {
    ModelASTPipelineDef dummyDef = new ModelASTPipelineDef(null);
    dummyDef.setStages(s);
    return dummyDef;
  }

  public ModelASTStages getStages() {
    for (ModelASTPipelineDef p : pipelineDefs) {
      ModelASTStages s = p.getStages();
      if (s.getUuid().toString().equals(stagesUUID)) {
        return s;
      }
    }
    return null;
  }

  public String getStagesUUID() {
    return stagesUUID;
  }

  public void setStagesUUID(String s) {
    this.stagesUUID = s;
  }

  public List<ModelASTStages> getStagesList() {
    List<ModelASTStages> stages = new ArrayList<>();
    for (ModelASTPipelineDef p : pipelineDefs) {
      stages.add(p.getStages());
    }
    return Collections.unmodifiableList(stages);
  }

  /** @deprecated Use {@link #addPipelineDef} instead. */
  @Deprecated
  public void addStages(ModelASTStages s) {
    ModelASTPipelineDef dummyDefForBackwardsCompat = new ModelASTPipelineDef(null);
    dummyDefForBackwardsCompat.setStages(s);
    this.pipelineDefs.add(dummyDefForBackwardsCompat);
  }

  /**
   * Get the main {@link ModelASTPipelineDef} for the build, returning {@code null} if there isn't
   * one or it can't be found.
   *
   * @see #getPipelineDefs
   */
  public ModelASTPipelineDef getPipelineDef() {
    for (ModelASTPipelineDef p : pipelineDefs) {
      if (p.getStages().getUuid().toString().equals(stagesUUID)) {
        return p;
      }
    }
    return null;
  }

  /**
   * Return an unmodifiable list of all instances of {@link ModelASTPipelineDef} attached to the
   * build, including those from shared libraries.
   *
   * @see #getPipelineDef
   */
  public List<ModelASTPipelineDef> getPipelineDefs() {
    return Collections.unmodifiableList(pipelineDefs);
  }

  public void addPipelineDef(ModelASTPipelineDef p) {
    this.pipelineDefs.add(p);
  }
}
