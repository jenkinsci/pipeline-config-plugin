package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashMap;
import java.util.Map;
import net.sf.json.JSONArray;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents a map of tool types to tool names (i.e., the name of the configured installation).
 * Corresponds to {@code Tools}.
 *
 * @author Andrew Bayer
 */
public final class ModelASTTools extends ModelASTElement {
  private Map<ModelASTKey, ModelASTValue> tools = new LinkedHashMap<>();

  public ModelASTTools(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONArray toJSON() {
    return toJSONArray(tools);
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, tools);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovyBlock("tools", tools, " ");
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(tools);
  }

  public Map<ModelASTKey, ModelASTValue> getTools() {
    return tools;
  }

  public void setTools(Map<ModelASTKey, ModelASTValue> tools) {
    this.tools = tools;
  }

  @Override
  public String toString() {
    return "ModelASTTools{" + "tools=" + tools + "}";
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

    ModelASTTools that = (ModelASTTools) o;

    return getTools() != null ? getTools().equals(that.getTools()) : that.getTools() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getTools() != null ? getTools().hashCode() : 0);
    return result;
  }
}
