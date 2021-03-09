package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashMap;
import java.util.Map;
import net.sf.json.JSONArray;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents a block of "foo = 'bar'" assignments to environment variables, corresponding to {@code
 * Environment}.
 *
 * @author Andrew Bayer
 */
public final class ModelASTEnvironment extends ModelASTElement {
  private Map<ModelASTKey, ModelASTEnvironmentValue> variables = new LinkedHashMap<>();

  public ModelASTEnvironment(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONArray toJSON() {
    return toJSONArray(variables);
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, variables);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovyBlock("environment", variables, " = ");
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(variables);
  }

  public Map<ModelASTKey, ModelASTEnvironmentValue> getVariables() {
    return variables;
  }

  public void setVariables(Map<ModelASTKey, ModelASTEnvironmentValue> variables) {
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ModelASTEnvironment{" + "variables=" + variables + "}";
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

    ModelASTEnvironment that = (ModelASTEnvironment) o;

    return getVariables() != null
        ? getVariables().equals(that.getVariables())
        : that.getVariables() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getVariables() != null ? getVariables().hashCode() : 0);
    return result;
  }
}
