/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAxis;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAxisContainer;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildConditionsContainer;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameter;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildParameters;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTExclude;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTExcludeAxis;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTExcludes;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTInternalFunctionCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTLibraries;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMatrix;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTMethodCall;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOptions;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTParallel;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostBuild;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStageBase;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStageInput;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTools;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTrigger;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTriggers;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhen;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenCondition;

/**
 * Abstract implementation of {@link ModelValidator}.
 *
 * <p>Use this class as a generic AST visitor instead of {@link ModelValidator} to prevent binary
 * compatibility issues in cases where it is fine to ignore any AST elements that were added to
 * Declarative after you extended this class.
 */
public class AbstractModelValidator implements ModelValidator {

  @Override
  public boolean validateElement(ModelASTAgent agent) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTBranch branch) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTBuildConditionsContainer container) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTPostBuild postBuild) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTPostStage post) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTBuildCondition buildCondition) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTEnvironment environment) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTTools tools) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTStep step) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTWhen when) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTMethodCall methodCall) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTOptions properties) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTTriggers triggers) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTBuildParameters buildParameters) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTOption jobProperty) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTTrigger trigger) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTBuildParameter buildParameter) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTPipelineDef pipelineDef) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTStageBase stage) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTStage stage, boolean isWithinParallel) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTStages stages) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTParallel parallel) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTMatrix matrix) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTAxisContainer axes) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTAxis axis) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTExcludes excludes) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTExclude exclude) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTExcludeAxis axis) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTLibraries libraries) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTWhenCondition condition) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTInternalFunctionCall call) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTStageInput input) {
    return true;
  }

  @Override
  public boolean validateElement(ModelASTValue value) {
    return true;
  }
}
