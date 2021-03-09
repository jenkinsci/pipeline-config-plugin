package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents a branch of Pipeline steps to execute, either as part of a parallel block, or on its
 * own.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTStage#branches
 */
public final class ModelASTBranch extends ModelASTElement {
  private String name;
  private List<ModelASTStep> steps = new ArrayList<>();

  public ModelASTBranch(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return new JSONObject().accumulate("name", name).accumulate("steps", toJSONArray(steps));
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, steps);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovy(steps);
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(steps);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<ModelASTStep> getSteps() {
    return steps;
  }

  public void setSteps(List<ModelASTStep> steps) {
    this.steps = steps;
  }

  @Override
  public String toString() {
    return "ModelASTBranch{" + "name='" + name + '\'' + ", steps=" + steps + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModelASTBranch)) return false;
    if (!super.equals(o)) return false;
    ModelASTBranch that = (ModelASTBranch) o;
    return Objects.equals(getName(), that.getName()) && Objects.equals(getSteps(), that.getSteps());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), getName(), getSteps());
  }
}
