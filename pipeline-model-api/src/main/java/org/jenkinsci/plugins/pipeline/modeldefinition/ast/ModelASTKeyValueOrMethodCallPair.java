package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * An individual pair of a {@link ModelASTKey} and a {@link ModelASTMethodArg}
 *
 * @author Andrew Bayer
 */
public final class ModelASTKeyValueOrMethodCallPair extends ModelASTElement
    implements ModelASTMethodArg {
  private ModelASTKey key;
  private ModelASTMethodArg value;

  public ModelASTKeyValueOrMethodCallPair(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return new JSONObject().accumulate("key", toJSON(key)).accumulate("value", toJSON(value));
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    validate(validator, key, value);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return key.toGroovy() + ": " + value.toGroovy();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(key, value);
  }

  public ModelASTKey getKey() {
    return key;
  }

  public void setKey(ModelASTKey key) {
    this.key = key;
  }

  public ModelASTMethodArg getValue() {
    return value;
  }

  public void setValue(ModelASTMethodArg value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return "ModelASTKeyValueOrMethodCallPair{" + "key=" + key + ", value=" + value + "}";
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

    ModelASTKeyValueOrMethodCallPair that = (ModelASTKeyValueOrMethodCallPair) o;

    if (getKey() != null ? !getKey().equals(that.getKey()) : that.getKey() != null) {
      return false;
    }
    return getValue() != null ? getValue().equals(that.getValue()) : that.getValue() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getKey() != null ? getKey().hashCode() : 0);
    result = 31 * result + (getValue() != null ? getValue().hashCode() : 0);
    return result;
  }
}
