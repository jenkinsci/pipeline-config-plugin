package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents the collection of {@code Stage}s to be executed in the build in parallel. Corresponds
 * to {@code Stages}. Used as a base to hold common functionality between parallel and matrix.
 *
 * @author Liam Newman
 */
public final class ModelASTMatrix extends ModelASTStageBase {

  private ModelASTAxisContainer axes;
  private ModelASTExcludes excludes;
  private ModelASTStages stages;

  public ModelASTMatrix(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    JSONObject o =
        super.toJSON()
            .elementOpt("axes", toJSON(axes))
            .elementOpt("excludes", toJSON(excludes))
            .elementOpt("stages", toJSON(stages));

    return o;
  }

  @Override
  public void validate(final ModelValidator validator) {
    super.validate(validator);
    validator.validateElement(this);
    validate(validator, axes, excludes);
    if (stages != null) {
      stages.validate(validator, true);
    }
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder children =
        new StringBuilder()
            .append(toGroovy(axes))
            .append(toGroovy(excludes))
            .append(super.toGroovy())
            .append(toGroovy(stages));

    return "matrix {\n" + children.toString() + "}\n";
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(axes, excludes, stages);
  }

  @Override
  public String toString() {
    return "ModelASTMatrix{"
        + ", axes="
        + axes
        + ", excludes="
        + excludes
        + ", "
        + super.toString()
        + "}";
  }

  public ModelASTAxisContainer getAxes() {
    return axes;
  }

  public void setAxes(ModelASTAxisContainer axes) {
    this.axes = axes;
  }

  public ModelASTExcludes getExcludes() {
    return excludes;
  }

  public void setExcludes(ModelASTExcludes excludes) {
    this.excludes = excludes;
  }

  public ModelASTStages getStages() {
    return stages;
  }

  public void setStages(ModelASTStages stages) {
    this.stages = stages;
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
    ModelASTMatrix that = (ModelASTMatrix) o;
    return Objects.equals(getAxes(), that.getAxes())
        && Objects.equals(getExcludes(), that.getExcludes())
        && Objects.equals(getStages(), that.getStages());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getAxes(), getExcludes(), getStages());
  }
}
