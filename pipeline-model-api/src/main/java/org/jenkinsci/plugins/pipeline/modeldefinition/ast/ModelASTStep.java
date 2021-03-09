package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Represents an individual step within any of the various blocks that can contain steps.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public class ModelASTStep extends ModelASTElement {
  /** @deprecated since 1.2-beta-4 */
  @Deprecated
  @Restricted(NoExternalUse.class)
  public static Map<String, String> blockedStepsBase() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    map.put("stage", Messages.ModelASTStep_BlockedSteps_Stage());
    map.put("properties", Messages.ModelASTStep_BlockedSteps_Properties());
    map.put("parallel", Messages.ModelASTStep_BlockedSteps_Parallel());
    return map;
  }

  /**
   * Use {@code
   * org.jenkinsci.plugins.pipeline.modeldefinition.validator.BlockedStepsAndMethodCalls.blockedInSteps()}
   * instead.
   *
   * @deprecated since 1.2-beta-4
   */
  @Deprecated
  @Restricted(NoExternalUse.class)
  public static Map<String, String> getBlockedSteps() {
    return blockedStepsBase();
  }

  private String name;
  private ModelASTArgumentList args;

  public ModelASTStep(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public JSONObject toJSON() {
    return new JSONObject().accumulate("name", name).elementOpt("arguments", toJSON(args));
  }

  @Override
  public void validate(@NonNull ModelValidator validator) {
    validator.validateElement(this);
    validate(validator, args);
  }

  @Override
  @NonNull
  public String toGroovy() {
    // Default to using whatever the original args structure is.
    ModelASTArgumentList argList = args;

    // If the args aren't null and they're a named list...
    if (args != null && args instanceof ModelASTNamedArgumentList) {
      ModelASTNamedArgumentList namedArgs = (ModelASTNamedArgumentList) args;
      // If the named list is exactly 1 long...
      if (namedArgs.getArguments().size() == 1) {
        DescriptorLookupCache lookup = DescriptorLookupCache.getPublicCache();
        Descriptor<? extends Describable> desc = lookup.lookupStepFirstThenFunction(name);
        DescribableModel<? extends Describable> model = lookup.modelForStepFirstThenFunction(name);

        // If we can lookup the model for this step or function...
        if (model != null) {
          DescribableParameter p = model.getSoleRequiredParameter();
          // If it's got a sole required parameter, that parameter is the key in our named list, and
          // it doesn't
          // take a closure...
          if (p != null
              && namedArgs.containsKeyName(p.getName())
              && !lookup.stepTakesClosure(desc)) {
            ModelASTValue value = namedArgs.valueForName(p.getName());

            // Set the arg list to instead be a ModelASTSingleArgument of that value.
            argList = new ModelASTSingleArgument(null);
            ((ModelASTSingleArgument) argList).setValue(value);
          }
        }
      }
    }

    return withOrWithoutParens(argList);
  }

  private String withOrWithoutParens(ModelASTArgumentList argList) {
    if (argList == null) {
      return name + "()";
    } else {
      String argGroovy = argList.toGroovy();
      if (!(this instanceof ModelASTTreeStep)
          && argList instanceof ModelASTSingleArgument
          &&
          // Special-casing for list/map args since they still need parentheses.
          !argGroovy.startsWith("[")) {
        return name + " " + argGroovy;
      } else {
        return name + "(" + argGroovy + ")";
      }
    }
  }

  @Override
  public void removeSourceLocation() {
    super.removeSourceLocation();
    removeSourceLocationsFrom(args);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ModelASTArgumentList getArgs() {
    return args;
  }

  public void setArgs(ModelASTArgumentList args) {
    this.args = args;
  }

  @Override
  public String toString() {
    return "ModelASTStep{" + "name='" + name + '\'' + ", args=" + args + "}";
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

    ModelASTStep that = (ModelASTStep) o;

    if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) {
      return false;
    }
    return getArgs() != null ? getArgs().equals(that.getArgs()) : that.getArgs() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getName() != null ? getName().hashCode() : 0);
    result = 31 * result + (getArgs() != null ? getArgs().hashCode() : 0);
    return result;
  }
}
