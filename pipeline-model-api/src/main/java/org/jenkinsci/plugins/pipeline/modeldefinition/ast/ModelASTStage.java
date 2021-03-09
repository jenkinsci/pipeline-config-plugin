package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringEscapeUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents an individual Stage and the {@link ModelASTBranch}s it may contain.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTPipelineDef
 */
public class ModelASTStage extends ModelASTStageBase {
  private String name;
  private ModelASTStages stages;
  private List<ModelASTBranch> branches = new ArrayList<>();
  private Boolean failFast;
  private ModelASTParallel parallel;
  private ModelASTMatrix matrix;

  @Deprecated private transient List<ModelASTStage> parallelContent = new ArrayList<>();

  public ModelASTStage(Object sourceLocation) {
    super(sourceLocation);
  }

  protected Object readResolve() throws IOException {
    if ((this.parallelContent != null && this.parallelContent.size() > 0)) {
      if (this.parallel == null) {
        this.parallel = new ModelASTParallel(new ArrayList<>());
      }
      this.parallel.getStages().addAll(this.parallelContent);
      this.parallelContent = new ArrayList<>();
    }

    return this;
  }

  @Override
  @NonNull
  public JSONObject toJSON() {

    JSONObject o =
        super.toJSON()
            .accumulate("name", name)
            .elementOpt("stages", toJSON(stages))
            .elementOpt("parallel", toJSON(parallel))
            .elementOpt("matrix", toJSON(matrix))
            .elementOpt("branches", nullIfEmpty(toJSONArray(branches)))
            .elementOpt("failFast", failFast);

    return o;
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validate(validator, false);
  }

  public void validate(final ModelValidator validator, boolean isWithinParallel) {
    super.validate(validator);
    validator.validateElement(this, isWithinParallel);
    validate(validator, branches, parallel, matrix);
    if (stages != null) {
      stages.validate(validator, isWithinParallel);
    }
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder result =
        new StringBuilder()
            // TODO decide if we need to support multiline names
            .append("stage(\'")
            .append(name.replace("'", "\\'"))
            .append("\') {\n")
            .append(super.toGroovy());

    result.append(toGroovy(stages));
    if (parallel != null || matrix != null) {
      if (failFast != null && failFast) {
        result.append("failFast true\n");
      }
    }
    result.append(toGroovy(parallel)).append(toGroovy(matrix));

    if (!branches.isEmpty()) {
      result.append("steps {\n");
      if (branches.size() > 1) {
        result.append("parallel(");
        boolean first = true;
        for (ModelASTBranch branch : branches) {
          if (first) {
            first = false;
          } else {
            result.append(',');
          }
          result.append('\n');
          result
              .append('"' + StringEscapeUtils.escapeJava(branch.getName()) + '"')
              .append(": {\n")
              .append(branch.toGroovy())
              .append("\n}");
        }
        if (failFast != null && failFast) {
          result.append(",\nfailFast: true");
        }
        result.append("\n)\n");
      } else if (branches.size() == 1) {
        result.append(branches.get(0).toGroovy());
      }

      result.append("}\n");
    }

    result.append("}\n");

    return result.toString();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(branches, stages, parallel, matrix);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ModelASTStages getStages() {
    return stages;
  }

  public void setStages(ModelASTStages stages) {
    this.stages = stages;
  }

  public List<ModelASTBranch> getBranches() {
    return branches;
  }

  public void setBranches(List<ModelASTBranch> branches) {
    this.branches = branches;
  }

  public Boolean getFailFast() {
    return failFast;
  }

  public void setFailFast(Boolean f) {
    this.failFast = f;
  }

  public ModelASTParallel getParallel() {
    return parallel;
  }

  public void setParallel(ModelASTParallel s) {
    this.parallel = s;
  }

  public ModelASTMatrix getMatrix() {
    return matrix;
  }

  public void setMatrix(ModelASTMatrix s) {
    this.matrix = s;
  }

  @Deprecated
  public List<ModelASTStage> getParallelContent() {
    return parallelContent;
  }

  @Deprecated
  public void setParallelContent(List<ModelASTStage> parallelContent) {
    this.parallelContent = parallelContent;
  }

  @Override
  public String toString() {
    return "ModelASTStage{"
        + "name='"
        + name
        + '\''
        + ", "
        + super.toString()
        + ", stages="
        + stages
        + ", branches="
        + branches
        + ", failFast="
        + failFast
        + ", parallel="
        + parallel
        + ", matrix="
        + matrix
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    ModelASTStage that = (ModelASTStage) o;
    return Objects.equals(getName(), that.getName())
        && Objects.equals(getStages(), that.getStages())
        && Objects.equals(getBranches(), that.getBranches())
        && Objects.equals(getFailFast(), that.getFailFast())
        && Objects.equals(getParallel(), that.getParallel())
        && Objects.equals(getMatrix(), that.getMatrix())
        && Objects.equals(getParallelContent(), that.getParallelContent());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        getName(),
        getStages(),
        getBranches(),
        getFailFast(),
        getParallel(),
        getMatrix(),
        getParallelContent());
  }
}
