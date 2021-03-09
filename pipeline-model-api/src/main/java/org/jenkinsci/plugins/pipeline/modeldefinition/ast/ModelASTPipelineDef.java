package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents the parsed pipeline definition for visual pipeline editor. Corresponds to {@code
 * Root}.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public final class ModelASTPipelineDef extends ModelASTElement {
  private ModelASTStages stages;
  private ModelASTPostBuild postBuild;
  private ModelASTEnvironment environment;
  private ModelASTAgent agent;
  private ModelASTTools tools;
  private ModelASTOptions options;
  private ModelASTBuildParameters parameters;
  private ModelASTTriggers triggers;
  private ModelASTLibraries libraries;

  public ModelASTPipelineDef(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    JSONObject a =
        new JSONObject()
            .elementOpt("stages", toJSON(stages))
            .elementOpt("post", toJSON(postBuild))
            .elementOpt("environment", toJSON(environment))
            .elementOpt("agent", toJSON(agent))
            .elementOpt("tools", toJSON(tools))
            .elementOpt("options", toJSONCheckEmpty(options))
            .elementOpt("parameters", toJSONCheckEmpty(parameters))
            .elementOpt("triggers", toJSONCheckEmpty(triggers))
            .elementOpt("libraries", toJSONCheckEmpty(libraries));

    return new JSONObject().accumulate("pipeline", a);
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    validator.validateElement(this);
    validate(
        validator,
        stages,
        postBuild,
        environment,
        agent,
        tools,
        options,
        parameters,
        triggers,
        libraries);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder result =
        new StringBuilder()
            .append("pipeline {\n")
            .append(toGroovy(agent))
            .append(toGroovy(libraries))
            .append(toGroovy(stages))
            .append(toGroovy(tools))
            .append(toGroovy(environment))
            .append(toGroovy(postBuild))
            .append(toGroovyCheckEmpty(options))
            .append(toGroovyCheckEmpty(parameters))
            .append(toGroovyCheckEmpty(triggers))
            .append("}\n");

    return result.toString();
  }

  /**
   * Helper method to pretty-print the generated Groovy from this and its children.
   *
   * @return An indented string of Groovy, suitable for use in a Jenkinsfile.
   */
  public String toPrettyGroovy() {
    return toIndentedGroovy(toGroovy());
  }

  public static String toIndentedGroovy(@NonNull String orig) {
    StringBuilder result = new StringBuilder();

    int indentCount = 0;
    boolean tripleSingleQuotedString = false;

    boolean first = true;
    for (String r : orig.split("\n")) {
      if (first) {
        first = false;
      } else {
        result.append('\n');
      }
      if (tripleSingleQuotedString) {
        result.append(r);
      } else {
        if (r.startsWith("}") || r.startsWith(")") || r.startsWith("]")) {
          indentCount--;
        }
        if (!StringUtils.isEmpty(r)) {
          result.append(indent(indentCount)).append(r);
        }
        if (r.endsWith("{") || r.endsWith("(") || r.endsWith("[")) {
          indentCount++;
        }
      }

      int index = r.indexOf("\'\'\'");
      while (index != -1) {
        tripleSingleQuotedString = !tripleSingleQuotedString;
        index = r.indexOf("\'\'\'", index + 3);
      }
    }

    return result.toString();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(
        agent, stages, libraries, postBuild, environment, tools, options, parameters, triggers);
  }

  private static String indent(int count) {
    return StringUtils.repeat("  ", count);
  }

  public ModelASTStages getStages() {
    return stages;
  }

  public void setStages(ModelASTStages stages) {
    this.stages = stages;
  }

  public ModelASTLibraries getLibraries() {
    return libraries;
  }

  public void setLibraries(ModelASTLibraries libraries) {
    this.libraries = libraries;
  }

  public ModelASTPostBuild getPostBuild() {
    return postBuild;
  }

  public void setPostBuild(ModelASTPostBuild postBuild) {
    this.postBuild = postBuild;
  }

  public ModelASTEnvironment getEnvironment() {
    return environment;
  }

  public void setEnvironment(ModelASTEnvironment environment) {
    this.environment = environment;
  }

  public ModelASTAgent getAgent() {
    return agent;
  }

  public void setAgent(ModelASTAgent agent) {
    this.agent = agent;
  }

  public ModelASTTools getTools() {
    return tools;
  }

  public void setTools(ModelASTTools tools) {
    this.tools = tools;
  }

  public ModelASTOptions getOptions() {
    return options;
  }

  public void setOptions(ModelASTOptions options) {
    this.options = options;
  }

  public ModelASTBuildParameters getParameters() {
    return parameters;
  }

  public void setParameters(ModelASTBuildParameters parameters) {
    this.parameters = parameters;
  }

  public ModelASTTriggers getTriggers() {
    return triggers;
  }

  public void setTriggers(ModelASTTriggers triggers) {
    this.triggers = triggers;
  }

  @Override
  public String toString() {
    return "ModelASTPipelineDef{"
        + "stages="
        + stages
        + ", post="
        + postBuild
        + ", environment="
        + environment
        + ", agent="
        + agent
        + ", tools="
        + tools
        + ", options="
        + options
        + ", parameters="
        + parameters
        + ", triggers="
        + triggers
        + ", libraries="
        + libraries
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

    ModelASTPipelineDef that = (ModelASTPipelineDef) o;

    if (getStages() != null ? !getStages().equals(that.getStages()) : that.getStages() != null) {
      return false;
    }
    if (getPostBuild() != null
        ? !getPostBuild().equals(that.getPostBuild())
        : that.getPostBuild() != null) {
      return false;
    }
    if (getEnvironment() != null
        ? !getEnvironment().equals(that.getEnvironment())
        : that.getEnvironment() != null) {
      return false;
    }
    if (getAgent() != null ? !getAgent().equals(that.getAgent()) : that.getAgent() != null) {
      return false;
    }
    if (getTools() != null ? !getTools().equals(that.getTools()) : that.getTools() != null) {
      return false;
    }
    if (getOptions() != null
        ? !getOptions().equals(that.getOptions())
        : that.getOptions() != null) {
      return false;
    }
    if (getParameters() != null
        ? !getParameters().equals(that.getParameters())
        : that.getParameters() != null) {
      return false;
    }
    if (getLibraries() != null
        ? !getLibraries().equals(that.getLibraries())
        : that.getLibraries() != null) {
      return false;
    }
    return getTriggers() != null
        ? getTriggers().equals(that.getTriggers())
        : that.getTriggers() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getStages() != null ? getStages().hashCode() : 0);
    result = 31 * result + (getPostBuild() != null ? getPostBuild().hashCode() : 0);
    result = 31 * result + (getEnvironment() != null ? getEnvironment().hashCode() : 0);
    result = 31 * result + (getAgent() != null ? getAgent().hashCode() : 0);
    result = 31 * result + (getTools() != null ? getTools().hashCode() : 0);
    result = 31 * result + (getOptions() != null ? getOptions().hashCode() : 0);
    result = 31 * result + (getParameters() != null ? getParameters().hashCode() : 0);
    result = 31 * result + (getTriggers() != null ? getTriggers().hashCode() : 0);
    result = 31 * result + (getLibraries() != null ? getLibraries().hashCode() : 0);
    return result;
  }
}
