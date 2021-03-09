package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/** @author Liam Newman */
public class ModelASTExcludeAxis extends ModelASTAxis {

  private Boolean inverse;

  public ModelASTExcludeAxis(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return super.toJSON().elementOpt("inverse", inverse);
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    super.validate(validator);
    validator.validateElement(this);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder argStr = new StringBuilder().append("name '").append(toGroovy(getName()) + "'\n");
    String values = toGroovyArgList(getValues());
    if (inverse != null && inverse) {
      argStr.append("notValues ").append(values).append("\n");
    } else {
      argStr.append("values ").append(values).append("\n");
    }
    return "axis {\n" + argStr.toString() + "}\n";
  }

  public Boolean getInverse() {
    return inverse;
  }

  public void setInverse(Boolean inverse) {
    this.inverse = inverse;
  }

  @Override
  public String toString() {
    return "ModelASTExcludeAxis{"
        + "name="
        + getName()
        + "values="
        + getValues()
        + "inverse="
        + inverse
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
    ModelASTExcludeAxis that = (ModelASTExcludeAxis) o;
    return Objects.equals(inverse, that.inverse);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), inverse);
  }
}
