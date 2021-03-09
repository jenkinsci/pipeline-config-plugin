package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;

/**
 * Represents the positional parameters for a step in a list of {@link ModelASTValue}s.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public final class ModelASTPositionalArgumentList extends ModelASTArgumentList {
  private List<ModelASTValue> arguments = new ArrayList<>();

  public ModelASTPositionalArgumentList(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONArray toJSON() {
    return toJSONArray(arguments);
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    // Nothing to validate directly
    validate(validator, arguments);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for (ModelASTValue argument : arguments) {
      if (first) {
        first = false;
      } else {
        result.append(", ");
      }
      result.append(argument.toGroovy());
    }
    return result.toString();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(arguments);
  }

  public List<ModelASTValue> getArguments() {
    return arguments;
  }

  public void setArguments(List<ModelASTValue> arguments) {
    this.arguments = arguments;
  }

  @Override
  public Map<String, ?> argListToMap() {
    List<Object> argList = new ArrayList<>();
    for (ModelASTValue v : arguments) {
      argList.add(v.getValue());
    }

    return Collections.singletonMap(UninstantiatedDescribable.ANONYMOUS_KEY, argList);
  }

  @Override
  public String toString() {
    return "ModelASTPositionalArgumentList{" + "arguments=" + arguments + "}";
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

    ModelASTPositionalArgumentList that = (ModelASTPositionalArgumentList) o;

    return getArguments() != null
        ? getArguments().equals(that.getArguments())
        : that.getArguments() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getArguments() != null ? getArguments().hashCode() : 0);
    return result;
  }
}
