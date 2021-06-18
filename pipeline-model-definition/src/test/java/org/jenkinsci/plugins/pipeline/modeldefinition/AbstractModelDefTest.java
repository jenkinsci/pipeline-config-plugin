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

import com.cloudbees.hudson.plugins.folder.Folder;
import com.google.common.collect.ImmutableList;
import hudson.model.*;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matcher;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.pipeline.modeldefinition.util.HasArchived;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.BlockedStepsAndMethodCalls;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariableList;
import org.jenkinsci.plugins.workflow.cps.global.WorkflowLibRepository;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.*;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ToolInstallations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import static com.jcabi.matchers.RegexMatchers.containsPattern;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Andrew Bayer
 */
public abstract class AbstractModelDefTest extends AbstractDeclarativeTest {

    private boolean defaultScriptSplitting = RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION;
    private boolean defaultScriptSplittingAllowLocalVariables = RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES;

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    @Rule public GitSampleRepoRule otherRepo = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule thirdRepo = new GitSampleRepoRule();

    protected static String legalAgentTypes = "";

    @Inject
    WorkflowLibRepository globalLibRepo;

    @Inject
    UserDefinedGlobalVariableList uvl;

    @BeforeClass
    public static void setUpPreClass() throws Exception {
        List<String> agentTypes = new ArrayList<>();

        for (DeclarativeAgentDescriptor d : j.jenkins.getExtensionList(DeclarativeAgentDescriptor.class)) {
            String symbol = symbolFromDescriptor(d);
            if (symbol != null) {
                agentTypes.add(symbol);
            }
        }
        legalAgentTypes = "[" + StringUtils.join(agentTypes.stream().sorted().collect(Collectors.toList()), ", ") + "]";
    }

    private static String symbolFromDescriptor(Descriptor d) {
        Symbol s = d.getClass().getAnnotation(Symbol.class);
        if (s != null) {
            return s.value()[0];
        }
        return null;
    }

    @Before
    public void setUpFeatureFlags() {
        defaultScriptSplitting = RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION;
        defaultScriptSplittingAllowLocalVariables = RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES;
    }

    @After
    public void cleanupFeatureFlags() {
        RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION = defaultScriptSplitting;
        RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = defaultScriptSplittingAllowLocalVariables;
    }



    @Before
    public void setUp() throws Exception {
        ToolInstallations.configureMaven3();
    }

    public static final List<String> SHOULD_PASS_CONFIGS = ImmutableList.of(
            "agent/agentAny",
            "agent/agentLabel",
            "agent/agentNoneWithNode",
            "basic/singleArgumentNullValue",
            "steps/metaStepSyntax",
            "environment/simpleEnvironment",
            "simpleScript",
            "postStage/simplePostBuild",
            "simpleTools",
            "options/simpleJobProperties",
            "simpleTriggers",
            "simpleParameters",
            "stringsNeedingEscapeLogic",
            "options/simpleWrapper",
            "multipleWrappers",
            "agent/multipleVariablesForAgent",
            "toolsInStage",
            "environment/environmentInStage",
            "parallel/parallelPipelineWithFailFast",
            "when/whenNestedCombinations",
            "when/whenEnv",
            "parallel/parallelPipelineWithSpaceInBranch",
            "parallel/parallelPipelineQuoteEscaping",
            "steps/nestedTreeSteps",
            "agent/inCustomWorkspace",
            "when/whenBeforeAgentTrue",
            "when/whenBeforeInputFalse",
            "environment/usernamePassword",
            "environment/environmentCrossReferences",
            "parallel/nestedParallelStages",
            "stagePost",
            "when/conditions/changelog/changelog",
            "when/conditions/changelog/changeset",
            "environment/backslashReductionInEnv",
            "stageWrapper",
            "matrix/matrixPipeline",
            "matrix/matrixPipelineTwoAxis",
            "matrix/matrixPipelineTwoAxisOneExclude",
            "matrix/matrixPipelineTwoAxisTwoExcludes",
            "matrix/matrixPipelineTwoAxisExcludeNot"
    );

    public static final List<String> CONVERT_ONLY_SHOULD_PASS_CONFIGS = ImmutableList.of(
            "simpleInput",
            "parametersInInput",
            "libraries/globalLibrarySuccess",
            "jsonSchemaNull",
            "parallel/parallelStagesFailFast",
            "parallel/parallelStagesFailFastWithOption",
            "parallel/parallelStagesGroupsAndStages",
            "basic/topLevelStageGroup",
            "agent/agentOnGroup"
    );

    public static Iterable<Object[]> configsWithErrors() {
        List<Object[]> result = new ArrayList<>();
        // First element is config name, second element is expected JSON error.
        result.add(new Object[]{"missingStages", Messages.JSONParser_MissingRequiredProperties("'stages'")});
        result.add(new Object[]{"missingAgent", Messages.JSONParser_MissingRequiredProperties("'agent'")});

        result.add(new Object[]{"emptyStages", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"emptyEnvironment", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"emptyPostBuild", Messages.JSONParser_TooFewItems(0, 1)});

        result.add(new Object[]{"rejectStageInSteps", Messages.ModelValidatorImpl_BlockedStep("stage",
                BlockedStepsAndMethodCalls.blockedInSteps().get("stage"))});
        result.add(new Object[]{"rejectParallelMixedInSteps", Messages.ModelValidatorImpl_BlockedStep("parallel",
                BlockedStepsAndMethodCalls.blockedInSteps().get("parallel"))});

        result.add(new Object[]{"stageWithoutName", Messages.JSONParser_MissingRequiredProperties("'name'")});

        result.add(new Object[]{"emptyParallel", Messages.ModelValidatorImpl_NothingForStage("foo")});

        result.add(new Object[]{"emptyJobProperties", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"emptyParameters", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"emptyTriggers", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"emptyWhen", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"mixedMethodArgs", Messages.ModelValidatorImpl_MixedNamedAndUnnamedParameters()});

        result.add(new Object[]{"rejectPropertiesStepInMethodCall",
                Messages.ModelValidatorImpl_BlockedStep("properties",
                        BlockedStepsAndMethodCalls.blockedInSteps().get("properties"))});

        result.add(new Object[]{"wrongParameterNameMethodCall", Messages.ModelValidatorImpl_InvalidStepParameter("namd", "name")});
        result.add(new Object[]{"invalidParameterTypeMethodCall", Messages.ModelValidatorImpl_InvalidParameterType("class java.lang.String", "name", "1234", Integer.class)});

        result.add(new Object[]{"perStageConfigEmptySteps", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"perStageConfigMissingSteps", Messages.JSONParser_MissingRequiredProperties("'steps'")});
        result.add(new Object[]{"perStageConfigUnknownSection", "additional properties are not allowed"});

        result.add(new Object[]{"unknownAgentType", Messages.ModelValidatorImpl_InvalidAgentType("foo", "[any, label, none, otherField]")});

        // Not using the full message here due to issues with the test extension in MultipleUnnamedParametersTest bleeding over in some situations.
        // That resulted in multiArgCtorProp sometimes showing up in the list of valid options, but not always. We still have the full test in
        // ValidatorTest#invalidWrapperType that does use the full message, though.
        result.add(new Object[]{"invalidWrapperType", "Invalid option type \"echo\". Valid option types:"});
        result.add(new Object[]{"invalidStageWrapperType", "Invalid option type \"echo\". Valid option types:"});

        result.add(new Object[]{"unknownBareAgentType", Messages.ModelValidatorImpl_InvalidAgentType("foo", legalAgentTypes)});
        result.add(new Object[]{"agentMissingRequiredParam", Messages.ModelValidatorImpl_MultipleAgentParameters("otherField", "[label, otherField]")});
        result.add(new Object[]{"agentUnknownParamForType", Messages.ModelValidatorImpl_InvalidAgentParameter("fruit", "otherField", "[label, otherField, nested]")});
        result.add(new Object[]{"notificationsSectionRemoved", "additional properties are not allowed"});
        result.add(new Object[]{"unknownWhenConditional", Messages.ModelValidatorImpl_UnknownWhenConditional("banana",
                "allOf, anyOf, archived, branch, buildingTag, changeRequest, changelog, changeset, environment, equals, expression, isRestartedRun, not, stashed, tag")});
        result.add(new Object[]{"whenInvalidParameterType", Messages.ModelValidatorImpl_InvalidUnnamedParameterType("class java.lang.String", 4, Integer.class)});
        result.add(new Object[]{"whenMissingRequiredParameter", Messages.ModelValidatorImpl_MissingRequiredStepParameter("value")});
        result.add(new Object[]{"whenUnknownParameter", Messages.ModelValidatorImpl_InvalidStepParameter("banana", "name")});

        //parallel
        result.add(new Object[]{"parallelStagesAndSteps", Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo")});
        result.add(new Object[]{"parallelStagesAndGroups", Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo")});
        result.add(new Object[]{"parallelStepsAndGroups", Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo")});
        result.add(new Object[]{"parallelStagesStepsAndGroups", Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo")});
        result.add(new Object[]{"parallelStagesAgentTools", Messages.ModelValidatorImpl_AgentInNestedStages("foo")});
        result.add(new Object[]{"parallelStagesDeepNesting", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
        result.add(new Object[]{"parallelStagesGroupsDeepNesting", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});

        //matrix
        // TODO: turn these back on when we update the json files
        result.add(new Object[]{"matrixStagesAndGroups", Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo")});
        result.add(new Object[]{"matrixStagesAndSteps", Messages.ModelValidatorImpl_TwoOfStepsStagesParallel("foo")});
        result.add(new Object[]{"matrixParallelStagesGroupsDeepNesting", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
        result.add(new Object[]{"parallelMatrixStagesGroupsDeepNesting", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
        result.add(new Object[]{"matrixStagesDeepNesting", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});


        result.add(new Object[]{"matrixTopLevel", Messages.JSONParser_MissingRequiredProperties("'stages'")});
//        result.add(new Object[]{"matrixAxisDuplicateName", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixAxisDuplicateValue", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixAxisMissingName", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixAxisMissingValues", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixAxisNonLiteralValue", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
        result.add(new Object[]{"matrixEmptyAxes", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"matrixEmptyExcludes", Messages.JSONParser_TooFewItems(0, 1)});
        result.add(new Object[]{"matrixEmptyExclude",  Messages.JSONParser_TooFewItems(0, 1)});
//        result.add(new Object[]{"matrixExcludeAxisDuplicateName", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixExcludeAxisDuplicateValue", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixExcludeAxisMissingValues", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixExcludeAxisMissingName", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
//        result.add(new Object[]{"matrixExcludeValuesWithValuesNot", Messages.ModelValidatorImpl_NoNestedWithinNestedStages()});
        result.add(new Object[]{"matrixMissingAxes", Messages.JSONParser_MissingRequiredProperties("'axes'")});
        result.add(new Object[]{"matrixMissingStages", Messages.JSONParser_MissingRequiredProperties("'stages'")});




        // TODO: Better error messaging for these schema violations.
        result.add(new Object[]{"nestedWhenWithArgs", "instance failed to match at least one schema"});
        result.add(new Object[]{"invalidWhenWithChildren", "instance failed to match at least one schema"});

        result.add(new Object[]{"malformed", "Unexpected close marker ']': expected '}'"});

        return result;
    }

    public static Iterable<Object[]> runtimeConfigsWithErrors() {
        List<Object[]> result = new ArrayList<>();
        for (Object[] e : configsWithErrors()) {
            result.add(e);
        }
        result.add(new Object[] { "notInstalledToolVersion",
                Messages.ModelValidatorImpl_NoToolVersion("maven", "apache-maven-3.0.2", "apache-maven-3.0.1")});

        return result;
    }

    protected WorkflowRun getAndStartBuild() throws Exception {
        return getAndStartBuild(null);
    }


    protected WorkflowRun getAndStartBuild(Folder folder) throws Exception {
        return getAndStartBuild(folder, null);
    }

    protected WorkflowRun getAndStartBuild(Folder folder, String projectName) throws Exception {
        WorkflowJob p = createWorkflowJob(folder, projectName);
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile"));
        return p.scheduleBuild2(0).waitForStart();
    }


    protected WorkflowRun getAndStartNonRepoBuild(String pipelineScriptFile) throws Exception {
        return getAndStartNonRepoBuild(null, pipelineScriptFile);
    }

    protected WorkflowRun getAndStartNonRepoBuild(Folder folder, String pipelineScriptFile) throws Exception {
        return getAndStartNonRepoBuild(folder, pipelineScriptFile, null);
    }

    protected WorkflowRun getAndStartNonRepoBuild(Folder folder, String pipelineScriptFile, String projectName) throws Exception {
        WorkflowJob p = createWorkflowJob(folder, projectName);
        p.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources(pipelineScriptFile), true));
        return p.scheduleBuild2(0).waitForStart();
    }

    private WorkflowJob createWorkflowJob(Folder folder) throws IOException {
        return createWorkflowJob(folder, null);
    }

    private WorkflowJob createWorkflowJob(Folder folder, String projectName) throws IOException {
        if (folder == null) {
            return j.createProject(WorkflowJob.class);
        } else {
            if (projectName == null) {
                projectName = "test" + (folder.getItems().size() + 1);
            }
            return folder.createProject(WorkflowJob.class, projectName);
        }
    }

    protected void initGlobalLibrary() throws IOException {
        // Need to do the injection by hand because we're not running with a RestartableJenkinsRule.
        j.jenkins.getInjector().injectMembers(this);
        File vars = new File(globalLibRepo.workspace, "vars");
        vars.mkdirs();
        FileUtils.writeStringToFile(new File(vars, "acmeVar.groovy"), StringUtils.join(Arrays.asList(
                "def hello(name) {echo \"Hello ${name}\"}",
                "def foo(x) { this.x = x+'-set'; }",
                "def bar() { return x+'-get' }",
                "def baz() { return 'nothing here' }",
                "def pipeline(Closure c) { c.call() }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "returnAThing.groovy"), StringUtils.join(Arrays.asList(
                "def call(a) { return \"${a} tada\" }"), "\n"
        ));
        FileUtils.writeStringToFile(new File(vars, "acmeFunc.groovy"), StringUtils.join(Arrays.asList(
                "def call(a,b) { echo \"call($a,$b)\" }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeFuncClosure1.groovy"), StringUtils.join(Arrays.asList(
                "def call(a, Closure body) { echo \"closure1($a)\"; body() }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeFuncClosure2.groovy"), StringUtils.join(Arrays.asList(
                "def call(a, b, Closure body) { echo \"closure2($a, $b)\"; body() }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeFuncMap.groovy"), StringUtils.join(Arrays.asList(
                "def call(m) { echo \"map call(${m.a},${m.b})\" }")
                , "\n"));
        FileUtils.writeStringToFile(new File(vars, "acmeBody.groovy"), StringUtils.join(Arrays.asList(
                "def call(body) { ",
                "  def config = [:]",
                "  body.resolveStrategy = Closure.DELEGATE_FIRST",
                "  body.delegate = config",
                "  body()",
                "  echo 'title was '+config.title",
                "}")
                , "\n"));

        // simulate the effect of push
        uvl.rebuild();
    }


    protected <T extends ParameterDefinition> T getParameterOfType(List<ParameterDefinition> params, Class<T> c) {
        for (ParameterDefinition p : params) {
            if (c.isInstance(p)) {
                return (T)p;
            }
        }
        return null;
    }

    protected EnvBuilder env(Slave s) {
        return new EnvBuilder(s);
    }

    protected ExpectationsBuilder expect(String resource) {
        return expect((String)null, resource);
    }

    protected ExpectationsBuilder expect(Result result, String resource) {
        return expect(result, null, resource);
    }

    protected ExpectationsBuilder expect(String resourceParent, String resource) {
        return new ExpectationsBuilder(resourceParent, resource);
    }

    protected ExpectationsBuilder expectError(String resource) {
        return expect(Result.FAILURE, "errors", resource);
    }

    protected ExpectationsBuilder expect(Result result, String resourceParent, String resource) {
        return new ExpectationsBuilder(result, resourceParent, resource);
    }

    public class ExpectationsBuilder {
        private Result result = Result.SUCCESS;
        private final String resourceParent;
        private String resource;
        private Map<String,String> otherResources;
        private List<String> logContains;
        private List<String> logNotContains;
        private List<String> logMatches;
        private String projectName;
        private WorkflowRun run;
        private boolean runFromRepo = true;
        private Folder folder; //We use the real stuff here, no mocking fluff
        private boolean hasFailureCause;
        private List<String> logContainsInOrder;
        private List<Matcher<Run>> buildMatchers;

        private ExpectationsBuilder(String resourceParent, String resource) {
            this(Result.SUCCESS, resourceParent, resource);
        }

        private ExpectationsBuilder(Result result, String resourceParent, String resource) {
            this.result = result;
            this.resourceParent = resourceParent;
            this.resource = resource;
            buildMatchers = new ArrayList<>();
            otherResources = new HashMap<>();
        }

        public ExpectationsBuilder otherResource(String resource, String filename) {
            this.otherResources.put(resource, filename);
            return this;
        }

        public ExpectationsBuilder logContainsInOrder(String... msgsInOrder) {
            this.logContainsInOrder = Arrays.asList(msgsInOrder);
            return this;
        }

        public ExpectationsBuilder runFromRepo(boolean mode) {
            runFromRepo = mode;
            return this;
        }

        public ExpectationsBuilder inFolder(Folder folder) {
            this.folder = folder;
            return this;
        }

        public ExpectationsBuilder withProjectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public ExpectationsBuilder logContains(String... logEntries) {
            if (this.logContains != null) {
                logContains.addAll(Arrays.asList(logEntries));
            } else {
                this.logContains = new ArrayList<>(Arrays.asList(logEntries));
            }
            return this;
        }

        public ExpectationsBuilder logMatches(String... logPatterns) {
            if (this.logMatches != null) {
                logMatches.addAll(Arrays.asList(logPatterns));
            } else {
                this.logMatches = new ArrayList<>(Arrays.asList(logPatterns));
            }
            return this;
        }

        public ExpectationsBuilder logNotContains(String... logEntries) {
            if (this.logNotContains != null) {
                this.logNotContains.addAll(Arrays.asList(logEntries));
            } else {
                this.logNotContains = new ArrayList<>(Arrays.asList(logEntries));
            }
            return this;
        }

        public ExpectationsBuilder hasFailureCase() {
            this.hasFailureCause = true;
            return this;
        }

        public ExpectationsBuilder buildMatches(Matcher<Run>... matchers) {
            buildMatchers.addAll(Arrays.asList(matchers));
            return this;
        }

        public ExpectationsBuilder archives(Matcher<String> fileName, Matcher<String> content, Charset encoding) {
            return buildMatches(HasArchived.hasArchivedString(fileName, content, encoding));
        }

        public ExpectationsBuilder archives(Matcher<String> fileName, Matcher<String> content) {
            return buildMatches(HasArchived.hasArchivedString(fileName, content));
        }

        public ExpectationsBuilder archives(String fileName, String content) {
            return buildMatches(HasArchived.hasArchivedString(equalTo(fileName), equalToCompressingWhiteSpace(content)));
        }

        public ExpectationsBuilder archives(String fileName, Matcher<String> content) {
            return buildMatches(HasArchived.hasArchivedString(equalTo(fileName), content));
        }

        public WorkflowRun go() throws Exception {
            String resourceFullName = resource;
            if (resourceParent != null) {
                resourceFullName = resourceParent + "/" + resource;
            }

            if (run == null) {
                if (runFromRepo) {
                    if (otherResources.isEmpty()) {
                        prepRepoWithJenkinsfile(resourceFullName);
                    } else {
                        prepRepoWithJenkinsfileAndOtherFiles(resourceFullName, otherResources);
                    }
                    run = getAndStartBuild(folder, projectName);
                } else {
                    run = getAndStartNonRepoBuild(folder, resourceFullName, projectName);
                }
            } else {
                run = run.getParent().scheduleBuild2(0).waitForStart();
            }
            j.waitForCompletion(run);
            // Calling `j.assertBuildStatus` directly after `j.waitForCompletion` is subject to race conditions in Pipeline jobs, so the call to `j.waitForMessage` is just here to ensure that the build is totally complete before checking the status.
            // If that race condition is fixed, the call to `j.waitForMessage` can be removed.
            j.waitForMessage("Finished: " + result, run); // like BuildListener.finished. 
            j.assertBuildStatus(result, run); // just double-checking

            if (logContains != null) {
                for (String entry : logContains) {
                    j.assertLogContains(entry, run);
                }
            }
            if (logNotContains != null) {
                for (String logNotContain : logNotContains) {
                    j.assertLogNotContains(logNotContain, run);
                }
            }
            if (logMatches != null) {
                String log = JenkinsRule.getLog(run);
                for (String pattern : logMatches) {
                    assertThat(log, containsPattern(pattern));
                }
            }
            if (hasFailureCause) {
                assertNotNull(run.getExecution().getCauseOfFailure());
            }
            if (logContainsInOrder != null && !logContainsInOrder.isEmpty()) {
                String buildLog = JenkinsRule.getLog(run);
                assertThat(buildLog, stringContainsInOrder(logContainsInOrder));
            }

            for (Matcher<Run> matcher : buildMatchers) {
                assertThat(run, matcher);
            }
            return run;
        }

        public ExpectationsBuilder resetForNewRun(Result result) {
            this.result = result;
            resource = null;
            logContains = null;
            logNotContains = null;
            buildMatchers = new ArrayList<>();
            return this;
        }

        public WorkflowRun getRun() {
            return run;
        }
    }

    public class EnvBuilder {
        private final Slave agent;
        private Map<String, String> env;

        protected EnvBuilder(Slave agent) {
            this.agent = agent;
            this.env = new HashMap<>();
            env.put("ONAGENT", "true");
        }

        public EnvBuilder put(String key, String value) {
            env.put(key, value);
            return this;
        }

        public void set() throws IOException {
            List<EnvironmentVariablesNodeProperty.Entry> entries = new ArrayList<>(env.size());
            for (Map.Entry<String, String> entry : env.entrySet()) {
                entries.add(new EnvironmentVariablesNodeProperty.Entry(entry.getKey(), entry.getValue()));
            }
            EnvironmentVariablesNodeProperty newProperty = new EnvironmentVariablesNodeProperty(entries);
            DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties = agent.getNodeProperties();
            nodeProperties.replace(newProperty);
        }

    }
}
