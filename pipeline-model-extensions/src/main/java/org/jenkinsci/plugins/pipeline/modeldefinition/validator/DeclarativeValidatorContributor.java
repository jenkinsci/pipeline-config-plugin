/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;

/**
 * Extension point for contributing additional {@link ModelASTElement} validation checks to {@link
 * ModelValidator} runs.
 *
 * <p>Override a {@code validateElement} method in your extension to have that check run at the end
 * of normal validation. The new method will take both the relevant {@link ModelASTElement} and a
 * possibly null {@link FlowExecution} for the current run, if there is one. Since validation can be
 * performed outside of the context of a run, such as from the CLI or via the REST endpoints (as
 * used by the editor, e.g.), you must handle a null execution cleanly.
 *
 * <p>Each method should return a string containing the error message if validation fails, and null
 * otherwise.
 */
public abstract class DeclarativeValidatorContributor implements ExtensionPoint {

  /** Implementations default to not optional. Can be overridden. */
  public boolean isOptional() {
    return false;
  }

  /** Fallback for any unknown element type. Always returns true, cannot be overridden. */
  @CheckForNull
  public final String validateElement(
      @NonNull ModelASTElement element, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public final List<String> validateElementAll(
      @NonNull ModelASTElement element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTAgent agent, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTAgent element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTBranch branch, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTBranch element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTBuildConditionsContainer container, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTBuildConditionsContainer element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTPostBuild postBuild, @CheckForNull FlowExecution execution) {
    return validateElement((ModelASTBuildConditionsContainer) postBuild, execution);
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTPostBuild element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTPostStage post, @CheckForNull FlowExecution execution) {
    return validateElement((ModelASTBuildConditionsContainer) post, execution);
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTPostStage element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTBuildCondition buildCondition, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTBuildCondition element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTEnvironment environment, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTEnvironment element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTTools tools, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTTools element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(@NonNull ModelASTStep step, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTStep element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(@NonNull ModelASTWhen when, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTWhen element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTMethodCall methodCall, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTMethodCall element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTOptions properties, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTOptions element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTTriggers triggers, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTTriggers element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTBuildParameters buildParameters, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTBuildParameters element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTOption jobProperty, @CheckForNull FlowExecution execution) {
    return validateElement((ModelASTMethodCall) jobProperty, execution);
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTOption element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTTrigger trigger, @CheckForNull FlowExecution execution) {
    return validateElement((ModelASTMethodCall) trigger, execution);
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTTrigger element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTBuildParameter buildParameter, @CheckForNull FlowExecution execution) {
    return validateElement((ModelASTMethodCall) buildParameter, execution);
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTBuildParameter element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTPipelineDef pipelineDef, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTPipelineDef element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTStage stage, boolean isNested, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTStage element, boolean isNested, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, isNested, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTStages stages, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTStages element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTLibraries libraries, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTLibraries element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTWhenCondition condition, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTWhenCondition element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTInternalFunctionCall call, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTInternalFunctionCall element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  @CheckForNull
  public String validateElement(
      @NonNull ModelASTValue value, @CheckForNull FlowExecution execution) {
    return null;
  }

  @NonNull
  public List<String> validateElementAll(
      @NonNull ModelASTValue element, @CheckForNull FlowExecution execution) {
    String r = validateElement(element, execution);
    List<String> result = new ArrayList<>();
    if (r != null) {
      result.add(r);
    }
    return result;
  }

  /**
   * Get all {@link DeclarativeValidatorContributor}s.
   *
   * @return a list of all {@link DeclarativeValidatorContributor}s registered.`
   */
  public static ExtensionList<DeclarativeValidatorContributor> all() {
    return ExtensionList.lookup(DeclarativeValidatorContributor.class);
  }
}
