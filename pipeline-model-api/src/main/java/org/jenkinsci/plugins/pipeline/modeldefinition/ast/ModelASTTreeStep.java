package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents the special case of a step that has a sub-block of further steps within it.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public class ModelASTTreeStep extends ModelASTStep {
  private List<ModelASTStep> children = new ArrayList<>();

  public ModelASTTreeStep(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return super.toJSON().accumulate("children", toJSONArray(children));
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    super.validate(validator);
    validate(validator, children);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovyBlock(super.toGroovy(), children);
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(children);
  }

  public List<ModelASTStep> getChildren() {
    return children;
  }

  public void setChildren(List<ModelASTStep> children) {
    this.children = children;
  }

  @Override
  public String toString() {
    return "ModelASTTreeStep{"
        + "name='"
        + getName()
        + '\''
        + ", args="
        + getArgs()
        + ", children="
        + children
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

    ModelASTTreeStep that = (ModelASTTreeStep) o;

    return getChildren() != null
        ? getChildren().equals(that.getChildren())
        : that.getChildren() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getChildren() != null ? getChildren().hashCode() : 0);
    return result;
  }
}
