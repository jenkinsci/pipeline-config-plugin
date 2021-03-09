/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package org.jenkinsci.plugins.pipeline.modeldefinition;

import static org.junit.Assert.assertNotNull;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.slaves.DumbSlave;
import java.util.Collections;
import jenkins.model.OptionalJobProperty;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.hamcrest.Matchers;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTOption;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostBuild;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.BlockedStepsAndMethodCalls;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.DeclarativeValidatorContributor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/** @author Andrew Bayer */
public class ValidatorTest extends AbstractModelDefTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private static DumbSlave s;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setLabelString("some-label");
  }

  @Issue("JENKINS-39011")
  @Test
  public void pipelineStepWithinOtherBlockFailure() throws Exception {
    expectError("pipelineStepWithinOtherBlocksFailure")
        .logContains(Messages.ModelParser_PipelineBlockNotAtTop(ModelStepLoader.STEP_NAME))
        .go();
  }

  @Test
  public void rejectStageInSteps() throws Exception {
    expectError("rejectStageInSteps")
        .logContains(
            Messages.ModelValidatorImpl_BlockedStep(
                "stage", BlockedStepsAndMethodCalls.blockedInSteps().get("stage")))
        .go();
  }

  @Test
  public void rejectParallelMixedInSteps() throws Exception {
    expectError("rejectParallelMixedInSteps")
        .logContains(
            Messages.ModelValidatorImpl_BlockedStep(
                "parallel", BlockedStepsAndMethodCalls.blockedInSteps().get("parallel")))
        .go();
  }

  @Test
  public void emptyStages() throws Exception {
    expectError("emptyStages").logContains(Messages.ModelValidatorImpl_NoStages()).go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void emptyStagesInGroup() throws Exception {
    expectError("emptyStagesInGroup").logContains(Messages.ModelValidatorImpl_NoStages()).go();
  }

  @Test
  public void emptyJobProperties() throws Exception {
    expectError("emptyJobProperties")
        .logContains(Messages.ModelValidatorImpl_EmptySection("options"))
        .go();
  }

  @Test
  public void emptyParameters() throws Exception {
    expectError("emptyParameters")
        .logContains(Messages.ModelValidatorImpl_EmptySection("parameters"))
        .go();
  }

  @Test
  public void emptyTriggers() throws Exception {
    expectError("emptyTriggers")
        .logContains(Messages.ModelValidatorImpl_EmptySection("triggers"))
        .go();
  }

  @Test
  public void matrixEmptyAxes() throws Exception {
    expectError("matrixEmptyAxes").logContains(Messages.ModelValidatorImpl_NoAxes()).go();
  }

  @Test
  public void matrixEmptyExcludes() throws Exception {
    expectError("matrixEmptyExcludes").logContains(Messages.ModelValidatorImpl_NoExcludes()).go();
  }

  @Test
  public void matrixEmptyExclude() throws Exception {
    expectError("matrixEmptyExclude").logContains(Messages.ModelValidatorImpl_NoAxes()).go();
  }

  @Test
  public void matrixMissingAxes() throws Exception {
    expectError("matrixMissingAxes")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("axes"))
        .go();
  }

  @Test
  public void matrixMissingStages() throws Exception {
    expectError("matrixMissingStages")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("stages"))
        .go();
  }

  @Test
  public void matrixAxisMissingName() throws Exception {
    expectError("matrixAxisMissingName")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("name"))
        .go();
  }

  @Test
  public void matrixAxisMissingValues() throws Exception {
    expectError("matrixAxisMissingValues")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("values"))
        .go();
  }

  @Test
  public void matrixAxisInvalidNameValues() throws Exception {
    expectError("matrixAxisInvalidNameValues")
        .logContains(Messages.ModelValidatorImpl_DuplicateAxisName("OS_VALUE"))
        .logContains(Messages.ModelValidatorImpl_DuplicateAxisValue("safari"))
        .logContains(Messages.ModelParser_ExpectedStringLiteral())
        .logContains("name \"${this_is_gstring_name}\"")
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv(""))
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("1NUMBER"))
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("$DOLLAR"))
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("HY-PHEN"))
        .logContains(
            Messages.ModelParser_ExpectedStringLiteralButGot("\"${this_is_gstring_value}\""))
        .logContains(Messages.ModelParser_ExpectedStringLiteralButGot("${[1, 2, 3, 4]}"))
        .logNotContains("_UNDERSCORE")
        .logNotContains(Messages.ModelValidatorImpl_DuplicateAxisName(""))
        .go();
  }

  @Test
  public void matrixExcludeAxisMissingName() throws Exception {
    expectError("matrixAxisMissingName")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("name"))
        .go();
  }

  @Test
  public void matrixExcludeAxisMissingValues() throws Exception {
    expectError("matrixAxisMissingValues")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("values"))
        .go();
  }

  @Test
  public void matrixExcludeAxisInvalidNameValues() throws Exception {
    expectError("matrixExcludeAxisInvalidNameValues")
        .logContains(Messages.ModelValidatorImpl_DuplicateAxisName("OS_VALUE"))
        .logContains(Messages.ModelValidatorImpl_DuplicateAxisValue("safari"))
        .logContains(Messages.ModelParser_ExpectedStringLiteral())
        .logContains("name \"${this_is_gstring_name}\"")
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv(""))
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("1NUMBER"))
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("$DOLLAR"))
        .logContains(Messages.ModelValidatorImpl_InvalidIdentifierInEnv("HY-PHEN"))
        .logContains(
            Messages.ModelParser_ExpectedStringLiteralButGot("\"${this_is_gstring_value}\""))
        .logNotContains("_UNDERSCORE")
        .logNotContains(Messages.ModelValidatorImpl_DuplicateAxisName(""))
        .go();
  }

  @Test
  public void matrixExcludeValuesWithNotValues() throws Exception {
    expectError("matrixExcludeValuesWithNotValues")
        .logContains(Messages.ModelParser_MatrixExcludeAxisValuesOrNotValues())
        .go();
  }

  @Test
  public void whenInvalidParameterType() throws Exception {
    expectError("whenInvalidParameterType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidUnnamedParameterType(
                "class java.lang.String", 4, Integer.class))
        .go();
  }

  @Test
  public void emptyWhen() throws Exception {
    expectError("emptyWhen").logContains(Messages.ModelValidatorImpl_EmptyWhen()).go();
  }

  @Test
  public void nestedWhenWithArgs() throws Exception {
    expectError("nestedWhenWithArgs")
        .logContains(Messages.ModelValidatorImpl_NestedWhenNoArgs("allOf"))
        .go();
  }

  @Test
  public void invalidWhenWithChildren() throws Exception {
    expectError("invalidWhenWithChildren")
        .logContains(Messages.ModelValidatorImpl_NoNestedWhenAllowed("branch"))
        .go();
  }

  @Test
  public void unknownWhenConditional() throws Exception {
    expectError("unknownWhenConditional")
        .logContains(
            Messages.ModelValidatorImpl_UnknownWhenConditional(
                "banana", StringUtils.join(DeclarativeStageConditionalDescriptor.allNames(), ", ")))
        .go();
  }

  @Test
  public void whenMissingRequiredParameter() throws Exception {
    expectError("whenMissingRequiredParameter")
        .logContains(Messages.ModelValidatorImpl_MissingRequiredStepParameter("value"))
        .go();
  }

  @Test
  public void whenUnknownParameter() throws Exception {
    expectError("whenUnknownParameter")
        .logContains(Messages.ModelValidatorImpl_InvalidStepParameter("banana", "name"))
        .go();
  }

  @Issue("JENKINS-41185")
  @Test
  public void whenNestedChildrenInvalid() throws Exception {
    expectError("whenNestedChildrenInvalid")
        .logContains(
            Messages.ModelValidatorImpl_NestedWhenWithoutChildren("allOf"),
            Messages.ModelValidatorImpl_NestedWhenWrongChildrenCount("not", 1))
        .go();
  }

  @Test
  public void blockInJobProperties() throws Exception {
    expectError("blockInJobProperties")
        .logContains(Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Options()))
        .go();
  }

  @Test
  public void blockInParameters() throws Exception {
    expectError("blockInParameters")
        .logContains(Messages.ModelParser_CannotHaveBlocks(Messages.Parser_BuildParameters()))
        .go();
  }

  @Test
  public void blockInTriggers() throws Exception {
    expectError("blockInTriggers")
        .logContains(Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Triggers()))
        .go();
  }

  @Test
  public void mixedMethodArgs() throws Exception {
    expectError("mixedMethodArgs")
        .logContains(Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters())
        .go();
  }

  @Test
  public void closureAsMethodCallArg() throws Exception {
    expectError("closureAsMethodCallArg")
        .logContains(Messages.ModelParser_MethodCallWithClosure())
        .go();
  }

  @Test
  public void tooFewMethodCallArgs() throws Exception {
    expectError("tooFewMethodCallArgs")
        .logContains(Messages.ModelValidatorImpl_WrongNumberOfStepParameters("cron", 1, 0))
        .go();
  }

  @Test
  public void wrongParameterNameMethodCall() throws Exception {
    expectError("wrongParameterNameMethodCall")
        .logContains(Messages.ModelValidatorImpl_InvalidStepParameter("namd", "name"))
        .go();
  }

  @Test
  public void invalidParameterTypeMethodCall() throws Exception {
    expectError("invalidParameterTypeMethodCall")
        .logContains(
            Messages.ModelValidatorImpl_InvalidParameterType(
                "class java.lang.String", "name", "1234", Integer.class))
        .go();
  }

  @Test
  public void rejectPropertiesStepInMethodCall() throws Exception {
    expectError("rejectPropertiesStepInMethodCall")
        .logContains(
            Messages.ModelValidatorImpl_BlockedStep(
                "properties", BlockedStepsAndMethodCalls.blockedInSteps().get("properties")))
        .go();
  }

  @Test
  public void rejectMapsForTriggerDefinition() throws Exception {
    expectError("rejectMapsForTriggerDefinition")
        .logContains(Messages.ModelParser_MapNotAllowed(Messages.Parser_Triggers()))
        .go();
  }

  @Test
  public void emptyParallel() throws Exception {
    expectError("emptyParallel")
        .logContains(Messages.ModelValidatorImpl_NothingForStage("foo"))
        .go();
  }

  @Test
  public void parallelPipelineWithInvalidFailFast() throws Exception {
    expectError("parallelPipelineWithInvalidFailFast")
        .logContains(Messages.ModelParser_ExpectedBoolean("failFast"))
        .go();
  }

  @Test
  public void parallelPipelineWithInvalidExtraKey() throws Exception {
    expectError("parallelPipelineWithInvalidExtraKey")
        .logContains(Messages.ModelParser_ExpectedClosureOrFailFast())
        .go();
  }

  @Test
  public void missingAgent() throws Exception {
    expectError("missingAgent")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("agent"))
        .go();
  }

  @Test
  public void missingStages() throws Exception {
    expectError("missingStages")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("stages"))
        .go();
  }

  @Test
  public void missingRequiredStepParameters() throws Exception {
    expectError("missingRequiredStepParameters")
        .logContains(Messages.ModelValidatorImpl_MissingRequiredStepParameter("time"))
        .go();
  }

  @Test
  public void invalidStepParameterType() throws Exception {
    expectError("invalidStepParameterType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidParameterType(
                "int", "time", "someTime", String.class))
        .go();
  }

  @Test
  public void invalidTriggerType() throws Exception {
    expectError("invalidTriggerType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "trigger", "banana", Triggers.getAllowedTriggerTypes().keySet()))
        .go();
  }

  @Test
  public void invalidParameterType() throws Exception {
    expectError("invalidParameterType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "parameter", "bananaParam", Parameters.getAllowedParameterTypes().keySet()))
        .go();
  }

  @Test
  public void invalidPropertiesType() throws Exception {
    expectError("invalidPropertiesType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "option", "banana", Options.getAllowedOptionTypes().keySet()))
        .go();
  }

  @Test
  public void unknownStepParameter() throws Exception {
    expectError("unknownStepParameter")
        .logContains(Messages.ModelValidatorImpl_InvalidStepParameter("banana", "unit"))
        .go();
  }

  @Test
  public void perStageConfigEmptySteps() throws Exception {
    expectError("perStageConfigEmptySteps").logContains(Messages.ModelValidatorImpl_NoSteps()).go();
  }

  @Test
  public void perStageConfigMissingSteps() throws Exception {
    expectError("perStageConfigMissingSteps")
        .logContains(Messages.ModelValidatorImpl_NothingForStage("foo"))
        .go();
  }

  @Test
  public void perStageConfigUnknownSection() throws Exception {
    expectError("perStageConfigUnknownSection")
        .logContains(Messages.ModelParser_UnknownStageSection("banana"))
        .go();
  }

  @Test
  public void invalidMetaStepSyntax() throws Exception {
    expectError("invalidMetaStepSyntax")
        .logContains(
            Messages.ModelValidatorImpl_InvalidStepParameter("someRandomField", "caseSensitive"))
        .go();
  }

  @Test
  public void duplicateStageNames() throws Exception {
    expectError("duplicateStageNames")
        .logContains(
            Messages.ModelValidatorImpl_DuplicateStageName("foo"),
            Messages.ModelValidatorImpl_NothingForStage("bar"))
        .go();
  }

  @Test
  public void duplicateEnvironment() throws Exception {
    expectError("duplicateEnvironment")
        .logContains(Messages.ModelParser_DuplicateEnvVar("FOO"))
        .go();
  }

  @Test
  public void duplicateStepParameter() throws Exception {
    expectError("duplicateStepParameter")
        .logContains("Duplicate named parameter 'time' found")
        .go();
  }

  @Test
  public void emptyEnvironment() throws Exception {
    expectError("emptyEnvironment").logContains(Messages.ModelValidatorImpl_NoEnvVars()).go();
  }

  @Test
  public void emptyAgent() throws Exception {
    expectError("emptyAgent")
        .logContains(Messages.ModelParser_InvalidSectionDefinition("agent"))
        .go();
  }

  @Test
  public void perStageConfigEmptyAgent() throws Exception {
    expectError("perStageConfigEmptyAgent")
        .logContains(Messages.ModelParser_InvalidStageSectionDefinition("agent"))
        .go();
  }

  @Test
  public void invalidBuildCondition() throws Exception {
    expectError("invalidBuildCondition")
        .logContains(
            "MultipleCompilationErrorsException: startup failed:",
            Messages.ModelValidatorImpl_InvalidBuildCondition(
                "banana", BuildCondition.getOrderedConditionNames()))
        .go();
  }

  @Test
  public void emptyPostBuild() throws Exception {
    expectError("emptyPostBuild")
        .logContains(Messages.ModelValidatorImpl_EmptySection("post"))
        .go();
  }

  @Test
  public void duplicatePostBuildConditions() throws Exception {
    expectError("duplicatePostBuildConditions")
        .logContains(Messages.ModelValidatorImpl_DuplicateBuildCondition("always"))
        .go();
  }

  @Test
  public void unlistedToolType() throws Exception {
    expectError("unlistedToolType")
        .logContains(
            "MultipleCompilationErrorsException: startup failed:",
            Messages.ModelValidatorImpl_InvalidSectionType(
                "tool", "banana", Tools.getAllowedToolTypes().keySet()))
        .go();
  }

  @Test
  public void notInstalledToolVersion() throws Exception {
    expectError("notInstalledToolVersion")
        .logContains(
            Messages.ModelValidatorImpl_NoToolVersion(
                "maven", "apache-maven-3.0.2", "apache-maven-3.0.1"))
        .go();
  }

  @Issue("JENKINS-45098")
  @Test
  public void toolWithoutVersion() throws Exception {
    expectError("toolWithoutVersion").logContains(Messages.ModelParser_ExpectedTool()).go();
  }

  @Test
  public void globalLibraryNonStepBody() throws Exception {
    initGlobalLibrary();

    // Test the case of a function with a body that doesn't consist of steps - this will fail.
    expectError("globalLibraryNonStepBody")
        .logContains(
            "MultipleCompilationErrorsException: startup failed:",
            Messages.ModelParser_ExpectedStep())
        .go();
  }

  @Test
  public void globalLibraryObjectMethodCall() throws Exception {
    initGlobalLibrary();

    // Test the case of calling a method on an object, i.e., foo.bar(1). This will fail.
    expectError("globalLibraryObjectMethodCall")
        .logContains(
            "MultipleCompilationErrorsException: startup failed:",
            Messages.ModelParser_ObjectMethodCall())
        .go();
  }

  @Test
  public void unknownAgentType() throws Exception {
    expectError("unknownAgentType")
        .logContains(Messages.ModelValidatorImpl_InvalidAgentType("foo", legalAgentTypes))
        .go();
  }

  @Test
  public void missingAgentType() throws Exception {
    expectError("missingAgentType")
        .logContains(Messages.ModelValidatorImpl_NoAgentType(legalAgentTypes))
        .go();
  }

  @Test
  public void unknownBareAgentType() throws Exception {
    expectError("unknownBareAgentType").logContains(Messages.ModelParser_InvalidAgent()).go();
  }

  @Test
  public void agentMissingRequiredParam() throws Exception {
    expectError("agentMissingRequiredParam")
        .logContains(
            Messages.ModelValidatorImpl_MultipleAgentParameters(
                "otherField", "[label, otherField]"))
        .go();
  }

  @Test
  public void multipleAgentTypes() throws Exception {
    expectError("multipleAgentTypes").logContains(Messages.ModelParser_OneAgentMax()).go();
  }

  @Test
  public void agentUnknownParamForType() throws Exception {
    expectError("agentUnknownParamForType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidAgentParameter(
                "fruit", "otherField", "[label, otherField, nested]"))
        .go();
  }

  @Test
  public void packageShouldNotSkipParsing() throws Exception {
    expectError("packageShouldNotSkipParsing")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("agent"))
        .go();
  }

  @Test
  public void importAndFunctionShouldNotSkipParsing() throws Exception {
    expectError("importAndFunctionShouldNotSkipParsing")
        .logContains(Messages.ModelValidatorImpl_RequiredSection("agent"))
        .go();
  }

  @Test
  public void invalidWrapperType() throws Exception {
    expectError("invalidWrapperType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "option", "echo", Options.getAllowedOptionTypes().keySet()))
        .go();
  }

  @Test
  public void notificationsSectionRemoved() throws Exception {
    expectError("notificationsSectionRemoved")
        .logContains(Messages.ModelParser_RenamedNotifications())
        .go();
  }

  @Issue("JENKINS-41645")
  @Test
  public void invalidEnvironmentIdentifiers() throws Exception {
    expectError("envIdentifiersCaughtInternally")
        .runFromRepo(false)
        .logContains(
            Messages.ModelValidatorImpl_InvalidIdentifierInEnv("1BAR"),
            Messages.ModelValidatorImpl_InvalidIdentifierInEnv("$DOLLAR"))
        .go();

    expectError("envIdentifierString")
        .runFromRepo(false)
        .logContains("[heyLook] is a constant expression, but it should be a variable expression")
        .go();

    expectError("envIdentifierHyphens")
        .runFromRepo(false)
        .logContains("(hey - look) is a binary expression, but it should be a variable expression")
        .go();

    expectError("envIdentifierDigitsUnderscore")
        .runFromRepo(false)
        .logContains("expecting token in range: '0'..'9', found 'A'")
        .go();
  }

  @Issue("JENKINS-39799")
  @Test
  public void badPostContent() throws Exception {
    expectError("badPostContent")
        .logContains(
            "MultipleCompilationErrorsException: startup failed:",
            Messages.ModelParser_InvalidBuildCondition(BuildCondition.getOrderedConditionNames()),
            Messages.ModelValidatorImpl_EmptySection("post"))
        .logNotContains("Caused by: java.lang.NullPointerException")
        .go();
  }

  @Test
  public void duplicateOptionAndTriggerNames() throws Exception {
    expectError("duplicateOptionAndTriggerNames")
        .logContains(
            Messages.ModelValidatorImpl_DuplicateOptionName("skipDefaultCheckout"),
            Messages.ModelValidatorImpl_DuplicateTriggerName("cron"))
        .go();
  }

  @Issue("JENKINS-38110")
  @Test
  public void emptyLibrariesDirective() throws Exception {
    expectError("emptyLibrariesDirective")
        .logContains(Messages.ModelValidatorImpl_EmptySection("libraries"))
        .go();
  }

  @Issue("JENKINS-38110")
  @Test
  public void invalidLibrariesDirectiveContent() throws Exception {
    expectError("invalidLibrariesDirectiveContent")
        .logContains(
            Messages.ModelValidatorImpl_EmptySection("libraries"),
            Messages.ModelParser_ExpectedLibrary("\"oh hi there\""),
            Messages.ModelParser_ExpectedLibrary("foo('bar')"),
            Messages.ModelParser_ExpectedLibrary("1 + 2"))
        .go();
  }

  @Issue("JENKINS-42550")
  @Test
  public void undefinedSectionReferencesCorrectly() throws Exception {
    expectError("undefinedSectionReferencesCorrectly")
        .logContains(Messages.Parser_UndefinedSection("node"), "node {")
        .logNotContains("pipeline {")
        .go();
  }

  @Issue("JENKINS-42771")
  @Test
  public void invalidMultiExpressionEnvironment() throws Exception {
    expectError("invalidMultiExpressionEnvironment")
        .logContains(
            Messages.ModelParser_InvalidEnvironmentOperation(),
            Messages.ModelParser_InvalidEnvironmentConcatValue())
        .go();
  }

  @Issue("JENKINS-42771")
  @Test
  public void additionalInvalidExpressionsInEnvironment() throws Exception {
    expectError("additionalInvalidExpressionsInEnvironment")
        .logContains(
            Messages.ModelParser_InvalidEnvironmentOperation(),
            Messages.ModelParser_InvalidEnvironmentConcatValue(),
            Messages.ModelParser_InvalidEnvironmentValue(),
            Messages.ModelParser_InvalidEnvironmentIdentifier("echo('HI THERE')"))
        .go();
  }

  @Issue("JENKINS-42858")
  @Test
  public void scriptSecurityRejectionInEnvironment() throws Exception {
    expectError("scriptSecurityRejectionInEnvironment")
        .logContains(
            "org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticField java.lang.System err")
        .go();
  }

  @Test
  public void scriptSecurityRejectionInWhenExpression() throws Exception {
    expectError("scriptSecurityRejectionInEnvironment")
        .logContains(
            "org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticField java.lang.System err")
        .go();
  }

  @Test
  public void scriptSecurityRejectionInSteps() throws Exception {
    expectError("scriptSecurityRejectionInEnvironment")
        .logContains(
            "org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticField java.lang.System err")
        .go();
  }

  @Issue("JENKINS-41334")
  @Test
  public void parallelStagesAgentTools() throws Exception {
    expectError("parallelStagesAgentTools")
        .logContains(
            Messages.ModelValidatorImpl_AgentInNestedStages("foo"),
            Messages.ModelValidatorImpl_ToolsInNestedStages("foo"))
        .go();
  }

  @Issue("JENKINS-41334")
  @Test
  public void parallelStagesAndSteps() throws Exception {
    expectError("parallelStagesAndSteps")
        .logContains(Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo"))
        .go();
  }

  @Issue("JENKINS-41334")
  @Test
  public void matrixStagesAgentTools() throws Exception {
    expectError("matrixStagesAgentTools")
        .logContains(
            Messages.ModelValidatorImpl_AgentInNestedStages("foo"),
            Messages.ModelValidatorImpl_ToolsInNestedStages("foo"))
        .go();
  }

  @Issue("JENKINS-41334")
  @Test
  public void matrixStagesAndSteps() throws Exception {
    expectError("matrixStagesAndSteps")
        .logContains(Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo"))
        .go();
  }

  @Issue("JENKINS-41334")
  @Test
  public void parallelStagesDeepNesting() throws Exception {
    expectError("parallelStagesDeepNesting")
        .logContains(Messages.ModelValidatorImpl_NoNestedWithinNestedStages())
        .go();
  }

  @Test
  public void matrixStagesDeepNesting() throws Exception {
    expectError("matrixStagesDeepNesting")
        .logContains(Messages.ModelValidatorImpl_NoNestedWithinNestedStages())
        .go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void parallelStagesGroupsDeepNesting() throws Exception {
    expectError("parallelStagesGroupsDeepNesting")
        .logContains(Messages.ModelValidatorImpl_NoNestedWithinNestedStages())
        .go();
  }

  @Test
  public void matrixParallelStagesGroupsDeepNesting() throws Exception {
    expectError("matrixParallelStagesGroupsDeepNesting")
        .logContains(Messages.ModelValidatorImpl_NoNestedWithinNestedStages())
        .go();
  }

  @Test
  public void parallelMatrixStagesGroupsDeepNesting() throws Exception {
    expectError("parallelMatrixStagesGroupsDeepNesting")
        .logContains(Messages.ModelValidatorImpl_NoNestedWithinNestedStages())
        .go();
  }

  @Test
  public void matrixTopLevel() throws Exception {
    expectError("matrixTopLevel").logContains(Messages.Parser_UndefinedSection("matrix")).go();
  }

  @Test
  public void parametersAndTriggersInOptions() throws Exception {
    expectError("parametersAndTriggersInOptions")
        .logContains(
            org.jenkinsci.plugins.pipeline.modeldefinition.validator.Messages
                .ParametersAndTriggersInOptions_RejectParameters(),
            org.jenkinsci.plugins.pipeline.modeldefinition.validator.Messages
                .ParametersAndTriggersInOptions_RejectTriggers())
        .go();
  }

  @Issue("JENKINS-46065")
  @Test
  public void validatorContributor() throws Exception {
    expectError("validatorContributor").logContains("testProperty is rejected").go();
  }

  @Issue("JENKINS-47814")
  @Test
  public void postValidatorContributor() throws Exception {
    TestDupeContributor.count = 0;
    expectError("postValidatorContributor")
        .logContains("validate 1")
        .logNotContains("validate 2")
        .go();
  }

  @Issue("JENKINS-47814")
  @Test
  public void optionValidatorContributor() throws Exception {
    TestDupeContributor.count = 0;
    expectError("optionValidatorContributor")
        .logContains("validate option 1")
        .logNotContains("validate option 2")
        .go();
  }

  @TestExtension
  public static class TestDupeContributor extends DeclarativeValidatorContributor {
    public static int count = 0;

    @Override
    public String validateElement(
        @NonNull ModelASTPostBuild postBuild, @CheckForNull FlowExecution execution) {
      count++;
      return "validate " + count;
    }

    @Override
    public String validateElement(
        @NonNull ModelASTOption option, @CheckForNull FlowExecution execution) {
      count++;
      return "validate option " + count;
    }
  }

  @TestExtension
  public static class RejectTestProperty extends DeclarativeValidatorContributor {
    @Override
    public String validateElement(
        @NonNull ModelASTOption option, @CheckForNull FlowExecution execution) {
      if (option.getName() != null && option.getName().equals("testProperty")) {
        return "testProperty is rejected";
      } else {
        return null;
      }
    }
  }

  public static class TestProperty extends OptionalJobProperty<WorkflowJob> {
    @DataBoundConstructor
    public TestProperty() {}

    @TestExtension
    @Symbol("testProperty")
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {
      @Override
      @NonNull
      public String getDisplayName() {
        return "Test job property to be rejected by a validator contributor.";
      }
    }
  }

  @Test
  public void notStageInStages() throws Exception {
    expectError("notStageInStages").logContains(Messages.ModelParser_ExpectedStage()).go();
  }

  @Test
  public void multipleTopLevelSections() throws Exception {
    expectError("multipleTopLevelSections")
        .logContains(Messages.Parser_MultipleOfSection("stages"))
        .go();
  }

  @Test
  public void multipleStageLevelSections() throws Exception {
    expectError("multipleStageLevelSections")
        .logContains(Messages.Parser_MultipleOfSection("agent"))
        .go();
  }

  @Issue("JENKINS-51792")
  @Test
  public void multipleStepsSectionsInStage() throws Exception {
    expectError("multipleStepsSectionsInStage")
        .logContains(Messages.Parser_MultipleOfSection("steps"))
        .go();
  }

  @Test
  public void nonBlockStages() throws Exception {
    expectError("nonBlockStages").logContains(Messages.ModelParser_ExpectedBlockFor("stages")).go();
  }

  @Test
  public void nonBlockSections() throws Exception {
    expectError("nonBlockSections")
        .logContains(
            Messages.ModelParser_ExpectedBlockFor("environment"),
            Messages.ModelParser_ExpectedBlockFor("libraries"),
            Messages.ModelParser_ExpectedBlockFor("options"),
            Messages.ModelParser_ExpectedBlockFor("triggers"),
            Messages.ModelParser_ExpectedBlockFor("parameters"),
            Messages.ModelParser_ExpectedBlockFor("tools"))
        .go();
  }

  @Issue("JENKINS-46544")
  @Test
  public void bareDollarCurly() throws Exception {
    expectError("bareDollarCurly")
        .logContains(
            Messages.ModelParser_BareDollarCurly("${env.BUILD_NUMBER}"),
            Messages.ModelParser_BareDollarCurly("${FOO}"))
        .go();
  }

  @Issue("JENKINS-47559")
  @Test
  public void whenContainingNonCondition() throws Exception {
    expectError("whenContainingNonCondition").logContains(Messages.ModelParser_ExpectedWhen()).go();
  }

  @Issue("JENKINS-47781")
  @Test
  public void specificDescribableMatch() throws Exception {
    expectError("specificDescribableMatch")
        .logContains(
            Messages.ModelValidatorImpl_InvalidStepParameter("upstreamWhat", "upstreamProjects"))
        .go();
  }

  @Issue("JENKINS-48380")
  @Test
  public void invalidStageWrapperType() throws Exception {
    expectError("invalidStageWrapperType")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "option", "echo", StageOptions.getAllowedOptionTypes().keySet()))
        .go();
  }

  @Issue("JENKINS-48380")
  @Test
  public void jobPropertyInStageOptions() throws Exception {
    expectError("jobPropertyInStageOptions")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "option", "buildDiscarder", StageOptions.getAllowedOptionTypes().keySet()))
        .go();
  }

  @Issue("JENKINS-48380")
  @Test
  public void invalidOptionInStage() throws Exception {
    expectError("invalidOptionInStage")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "option", "skipStagesAfterUnstable", StageOptions.getAllowedOptionTypes().keySet()))
        .go();
  }

  @Issue("JENKINS-48379")
  @Test
  public void inputWithoutMessage() throws Exception {
    expectError("inputWithoutMessage")
        .logContains(Messages.ModelValidatorImpl_MissingInputMessage())
        .go();
  }

  @Issue("JENKINS-48379")
  @Test
  public void invalidInputSection() throws Exception {
    expectError("invalidInputSection")
        .logContains(Messages.ModelParser_InvalidInputField("banana"))
        .go();
  }

  @Issue("JENKINS-48379")
  @Test
  public void duplicateInputFields() throws Exception {
    expectError("duplicateInputFields")
        .logContains(Messages.Parser_MultipleOfSection("message"))
        .go();
  }

  @Issue("JENKINS-48379")
  @Test
  public void invalidParameterTypeInInput() throws Exception {
    expectError("invalidParameterTypeInInput")
        .logContains(
            Messages.ModelValidatorImpl_InvalidSectionType(
                "parameter", "bananaParam", Parameters.getAllowedParameterTypes().keySet()))
        .go();
  }

  @Issue("JENKINS-49070")
  @Test
  public void bigIntegerFailure() throws Exception {
    expectError("bigIntegerFailure").logContains(Messages.ModelParser_BigIntegerValue()).go();
  }

  @Test
  public void enableOptionalValidator() throws Exception {
    String script = pipelineSourceFromResources("simplePipeline");

    assertNotNull(Converter.scriptToPipelineDef(script));

    thrown.expect(MultipleCompilationErrorsException.class);
    thrown.expectMessage(Matchers.containsString("Echo is banned"));
    Converter.scriptToPipelineDef(
        script,
        Collections.<Class<? extends DeclarativeValidatorContributor>>singletonList(
            RejectEchoStep.class));
  }

  @TestExtension
  public static class RejectEchoStep extends DeclarativeValidatorContributor {
    @CheckForNull
    @Override
    public String validateElement(
        @NonNull ModelASTStep step, @CheckForNull FlowExecution execution) {
      if (step.getName() != null && step.getName().equals("echo")) {
        return "Echo is banned";
      }

      return null;
    }

    @Override
    public boolean isOptional() {
      return true;
    }
  }

  @Issue("JENKINS-46809")
  @Test
  public void parallelStagesAndGroups() throws Exception {
    expectError("parallelStagesAndGroups")
        .logContains(Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo"))
        .go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void parallelStepsAndGroups() throws Exception {
    expectError("parallelStepsAndGroups")
        .logContains(Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo"))
        .go();
  }

  @Issue("JENKINS-46809")
  @Test
  public void parallelStagesStepsAndGroups() throws Exception {
    expectError("parallelStagesStepsAndGroups")
        .logContains(Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo"))
        .go();
  }

  @Issue("JENKINS-51828")
  @Test
  public void incorrectNestedStagesNPE() throws Exception {
    expectError("incorrectNestedStagesNPE").logContains(Messages.ModelParser_ExpectedStage()).go();
  }

  @Test
  public void matrixStagesAndGroups() throws Exception {
    expectError("matrixStagesAndGroups")
        .logContains(Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo"))
        .go();
  }
}
