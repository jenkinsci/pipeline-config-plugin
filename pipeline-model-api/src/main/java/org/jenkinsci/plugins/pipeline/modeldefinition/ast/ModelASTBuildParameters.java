package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * A container for one or more {@link ModelASTBuildParameter}s.
 *
 * @author Andrew Bayer
 */
public final class ModelASTBuildParameters extends ModelASTElement
    implements ModelASTElementContainer {
  private List<ModelASTBuildParameter> parameters = new ArrayList<>();

  public ModelASTBuildParameters(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return toJSONObject("parameters", parameters);
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, parameters);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovyBlock("parameters", parameters);
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(parameters);
  }

  public boolean isEmpty() {
    return parameters.isEmpty();
  }

  public List<ModelASTBuildParameter> getParameters() {
    return parameters;
  }

  public void setParameters(List<ModelASTBuildParameter> parameters) {
    this.parameters = parameters;
  }

  @Override
  public String toString() {
    return "ModelASTBuildParameters{" + "parameters=" + parameters + ", " + super.toString() + "}";
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

    ModelASTBuildParameters that = (ModelASTBuildParameters) o;

    return getParameters() != null
        ? getParameters().equals(that.getParameters())
        : that.getParameters() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getParameters() != null ? getParameters().hashCode() : 0);
    return result;
  }
}
