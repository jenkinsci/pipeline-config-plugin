package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents what context in which to run the build - i.e., which label to run on, what Docker
 * agent to run in, etc. Corresponds to Agent.
 *
 * @author Andrew Bayer
 */
public final class ModelASTAgent extends ModelASTElement {
  private ModelASTMethodArg variables;
  private ModelASTKey agentType;

  public ModelASTAgent(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    final JSONObject j = new JSONObject();

    // Handle JENKINS-43016 - round-trip empty-string label agent into agent any.
    if (isEmptyStringLabelAgent()) {
      j.accumulate("type", "any");
    } else {
      j.accumulate("type", toJSON(agentType));

      if (variables instanceof ModelASTClosureMap) {
        j.elementOpt("arguments", nullIfEmpty((JSONArray) variables.toJSON()));
      } else if (variables instanceof ModelASTValue) {
        j.elementOpt("argument", variables.toJSON());
      }
    }
    return j;
  }

  private boolean isEmptyStringLabelAgent() {
    if (agentType.getKey().equals("label") || agentType.getKey().equals("node")) {
      if (variables instanceof ModelASTValue && "".equals(((ModelASTValue) variables).getValue())) {
        return true;
      }
      if (variables instanceof ModelASTClosureMap) {
        Map<ModelASTKey, ModelASTMethodArg> vars = ((ModelASTClosureMap) variables).getVariables();
        // Don't actually switch to "agent any" if there are any additional options besides the
        // label.
        if (vars.size() == 1) {
          for (Map.Entry<ModelASTKey, ModelASTMethodArg> entry : vars.entrySet()) {
            if (entry.getKey().getKey().equals("label")) {
              ModelASTMethodArg argValue = entry.getValue();
              if (argValue instanceof ModelASTValue
                  && ((ModelASTValue) argValue).getValue().equals("")) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, variables);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder argStr = new StringBuilder();
    if (variables == null
        || (variables instanceof ModelASTClosureMap
            && ((ModelASTClosureMap) variables).getVariables().isEmpty())) {
      argStr.append(agentType.toGroovy());
    } else {
      argStr.append("{\n");
      argStr.append(agentType.toGroovy());
      argStr.append(" ");
      argStr.append(variables.toGroovy());
      argStr.append("\n}");
    }

    return "agent " + argStr.toString() + "\n";
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(agentType, variables);
  }

  public ModelASTKey getAgentType() {
    return agentType;
  }

  public void setAgentType(ModelASTKey k) {
    this.agentType = k;
  }

  public ModelASTMethodArg getVariables() {
    return variables;
  }

  public void setVariables(ModelASTMethodArg variables) {
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "ModelASTAgent{" + "agentType=" + agentType + "variables=" + variables + "}";
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

    ModelASTAgent that = (ModelASTAgent) o;

    if (getAgentType() != null
        ? !getAgentType().equals(that.getAgentType())
        : that.getAgentType() != null) {
      return false;
    }

    return getVariables() != null
        ? getVariables().equals(that.getVariables())
        : that.getVariables() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getAgentType() != null ? getAgentType().hashCode() : 0);
    result = 31 * result + (getVariables() != null ? getVariables().hashCode() : 0);
    return result;
  }
}
