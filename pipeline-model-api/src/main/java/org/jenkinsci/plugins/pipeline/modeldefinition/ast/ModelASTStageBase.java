package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

/**
 * Represents an individual Stage and the {@link ModelASTBranch}s it may contain.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTPipelineDef
 */
public abstract class ModelASTStageBase extends ModelASTElement {
  private ModelASTAgent agent;
  private ModelASTPostStage post;
  private ModelASTWhen when;
  private ModelASTTools tools;
  private ModelASTEnvironment environment;
  private ModelASTOptions options;
  private ModelASTStageInput input;

  protected ModelASTStageBase(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    JSONObject o =
        new JSONObject()
            .elementOpt("agent", toJSON(agent))
            .elementOpt("when", toJSON(when))
            .elementOpt("post", toJSON(post))
            .elementOpt("tools", toJSON(tools))
            .elementOpt("environment", toJSON(environment))
            .elementOpt("options", toJSON(options))
            .elementOpt("input", toJSON(input));

    return o;
  }

  @Override
  public void validate(@NonNull final ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, agent, when, post, tools, environment, options, input);
  }

  @Override
  @NonNull
  public String toGroovy() {
    StringBuilder result =
        new StringBuilder()
            .append(toGroovy(agent))
            .append(toGroovy(when))
            .append(toGroovy(tools))
            .append(toGroovy(environment))
            .append(toGroovy(options))
            .append(toGroovy(input))
            .append(toGroovy(post));

    return result.toString();
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(agent, when, post, tools, environment, options, input);
  }

  public ModelASTAgent getAgent() {
    return agent;
  }

  public void setAgent(ModelASTAgent agent) {
    this.agent = agent;
  }

  public ModelASTPostStage getPost() {
    return post;
  }

  public void setPost(ModelASTPostStage post) {
    this.post = post;
  }

  public ModelASTWhen getWhen() {
    return when;
  }

  public void setWhen(ModelASTWhen when) {
    this.when = when;
  }

  public ModelASTTools getTools() {
    return tools;
  }

  public void setTools(ModelASTTools tools) {
    this.tools = tools;
  }

  public ModelASTEnvironment getEnvironment() {
    return environment;
  }

  public void setEnvironment(ModelASTEnvironment environment) {
    this.environment = environment;
  }

  public ModelASTOptions getOptions() {
    return options;
  }

  public void setOptions(ModelASTOptions options) {
    this.options = options;
  }

  public ModelASTStageInput getInput() {
    return input;
  }

  public void setInput(ModelASTStageInput input) {
    this.input = input;
  }

  @Override
  public String toString() {
    return "agent="
        + agent
        + ", when="
        + when
        + ", post="
        + post
        + ", tools="
        + tools
        + ", environment="
        + environment
        + ", options="
        + options
        + ", input="
        + input;
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
    ModelASTStageBase that = (ModelASTStageBase) o;
    return Objects.equals(getAgent(), that.getAgent())
        && Objects.equals(getPost(), that.getPost())
        && Objects.equals(getWhen(), that.getWhen())
        && Objects.equals(getTools(), that.getTools())
        && Objects.equals(getEnvironment(), that.getEnvironment())
        && Objects.equals(getOptions(), that.getOptions())
        && Objects.equals(getInput(), that.getInput());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        getAgent(),
        getPost(),
        getWhen(),
        getTools(),
        getEnvironment(),
        getOptions(),
        getInput());
  }
}
