package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * A container for one or more {@link ModelASTTrigger}s.
 *
 * @author Andrew Bayer
 */
public final class ModelASTTriggers extends ModelASTElement implements ModelASTElementContainer {
  private List<ModelASTTrigger> triggers = new ArrayList<>();

  public ModelASTTriggers(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return toJSONObject("triggers", triggers);
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, triggers);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovyBlock("triggers", triggers);
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(triggers);
  }

  public boolean isEmpty() {
    return triggers.isEmpty();
  }

  public List<ModelASTTrigger> getTriggers() {
    return triggers;
  }

  public void setTriggers(List<ModelASTTrigger> triggers) {
    this.triggers = triggers;
  }

  @Override
  public String toString() {
    return "ModelASTTriggers{" + "triggers=" + triggers + "}";
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

    ModelASTTriggers that = (ModelASTTriggers) o;

    return getTriggers() != null
        ? getTriggers().equals(that.getTriggers())
        : that.getTriggers() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getTriggers() != null ? getTriggers().hashCode() : 0);
    return result;
  }
}
