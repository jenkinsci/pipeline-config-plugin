package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents a single BuildCondition to be checked and possibly executed in either the PostBuild or
 * Notifications sections.
 *
 * @author Andrew Bayer
 */
public final class ModelASTBuildCondition extends ModelASTElement {
  private String condition;
  private ModelASTBranch branch;

  public ModelASTBuildCondition(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return new JSONObject().accumulate("condition", condition).accumulate("branch", toJSON(branch));
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, branch);
  }

  @Override
  @NonNull
  public String toGroovy() {
    return toGroovyBlock(condition, branch);
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(branch);
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public ModelASTBranch getBranch() {
    return branch;
  }

  public void setBranch(ModelASTBranch branch) {
    this.branch = branch;
  }

  @Override
  public String toString() {
    return "ModelASTBuildCondition{"
        + "condition='"
        + condition
        + '\''
        + ", branch="
        + branch
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

    ModelASTBuildCondition that = (ModelASTBuildCondition) o;

    if (getCondition() != null
        ? !getCondition().equals(that.getCondition())
        : that.getCondition() != null) {
      return false;
    }
    return getBranch() != null ? getBranch().equals(that.getBranch()) : that.getBranch() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getCondition() != null ? getCondition().hashCode() : 0);
    result = 31 * result + (getBranch() != null ? getBranch().hashCode() : 0);
    return result;
  }
}
