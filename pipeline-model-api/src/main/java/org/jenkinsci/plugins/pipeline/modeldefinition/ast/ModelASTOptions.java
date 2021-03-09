package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * A container for one or more {@link ModelASTOption}s
 *
 * @author Andrew Bayer
 */
public final class ModelASTOptions extends ModelASTElement implements ModelASTElementContainer {
  private List<ModelASTOption> options = new ArrayList<>();
  private boolean inStage = false;

  public ModelASTOptions(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return toJSONObject("options", options);
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, options);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovyBlock("options", options);
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(options);
  }

  @Override
  public boolean isEmpty() {
    return options.isEmpty();
  }

  public List<ModelASTOption> getOptions() {
    return options;
  }

  public void setOptions(List<ModelASTOption> options) {
    this.options = options;
  }

  public boolean isInStage() {
    return inStage;
  }

  public void setInStage(boolean inStage) {
    this.inStage = inStage;
  }

  @Override
  public String toString() {
    return "ModelASTOptions{" + "options=" + options + ",inStage=" + inStage + "}";
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

    ModelASTOptions that = (ModelASTOptions) o;

    if (!isInStage() == that.isInStage()) {
      return false;
    }
    return getOptions() != null
        ? getOptions().equals(that.getOptions())
        : that.getOptions() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getOptions() != null ? getOptions().hashCode() : 0);
    result = 31 * result + (isInStage() ? 1 : 0);
    return result;
  }
}
