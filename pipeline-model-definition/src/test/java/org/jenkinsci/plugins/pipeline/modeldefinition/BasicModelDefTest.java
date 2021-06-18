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

import com.google.common.base.Predicate;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.tasks.LogRotator;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.jenkinsci.plugins.workflow.steps.ErrorStep;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

/**
 * @author Andrew Bayer
 */
public class BasicModelDefTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setNumExecutors(10);
        s.setLabelString("some-label");
    }

    @Issue("JENKINS-47363")
    // Give this a longer timeout
    @Test(timeout=5 * 60 * 1000)
    public void stages300() throws Exception {
        RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION = true;
        expect("basic/stages300")
            .logContains("letters1 = 'a', letters10 = 'a', letters100 = 'a'",
                "letters1 = 'j', letters10 = 'j', letters100 = 'c'")
            .logNotContains("List expressions can only contain up to 250 elements")
            .go();
    }

    @Test
    public void stages300NoSplit() throws Exception {
        RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION = false;
        expect(Result.FAILURE, "basic/stages300")
            .logNotContains("letters1 = 'a', letters10 = 'a', letters100 = 'a'",
                "letters1 = 'j', letters10 = 'j', letters100 = 'c'")
            .logContains("List expressions can only contain up to 250 elements")
            .go();
    }

    @Issue("JENKINS-37984")
    @Test
    public void stages100WithOutsideVarAndFunc() throws Exception {
        // this should have same behavior whether script splitting is enable or not
        RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;
        expect("basic/stages100WithOutsideVarAndFunc")
            .logContains("letters1 = 'a', letters10 = 'a', letters100 = 'a'",
                "letters1 = 'j', letters10 = 'j', letters100 = 'a'",
                "Hi there - This comes from a function")
            .logNotContains("Method code too large!")
            .go();
    }

    @Issue("JENKINS-37984")
    @Test
    public void stages100WithOutsideVarAndFuncNoSplitting() throws Exception {
        RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION = false;
        expect("basic/stages100WithOutsideVarAndFunc")
            .logContains("letters1 = 'a', letters10 = 'a', letters100 = 'a'",
                "letters1 = 'j', letters10 = 'j', letters100 = 'a'",
                "Hi there - This comes from a function")
            .logNotContains("Method code too large!")
            .go();
    }

    @Issue("JENKINS-37984")
    @Test
    public void stages100WithOutsideVarAndFuncNotAllowed() throws Exception {
        // this test only works if script splitting is enabled
        Assume.assumeThat(RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION, is(true));

        RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = false;
        expect(Result.FAILURE,"basic/stages100WithOutsideVarAndFunc")
            .logContains("add the '@Field' annotation to these local variable declarations")
            .logContains("firstVar, secondVar, someVar")
            .logNotContains("Method code too large!")
            .go();
    }

    @Test
    public void failingPipeline() throws Exception {
        expect(Result.FAILURE, "basic/failingPipeline")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "goodbye",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .hasFailureCase()
                .go();
    }

    @Test
    public void failingPostBuild() throws Exception {
        expect(Result.FAILURE, "basic/failingPostBuild")
                .logContains("[Pipeline] { (foo)",
                        "hello",
                        "goodbye",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .hasFailureCase()
                .go();
    }

    @Issue("JENKINS-38097")
    @Test
    public void allStagesExist() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "basic/allStagesExist")
                .logContains("[Pipeline] { (foo)", "hello", "[Pipeline] { (bar)")
                .hasFailureCase()
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof StepStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((StepStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNotNull(endFoo.getError());

        FlowNode shouldBeFailedNode = execution.getNode("" + (Integer.valueOf(endFoo.getId()) - 1));
        assertNotNull(shouldBeFailedNode);
        assertNotNull(shouldBeFailedNode.getError());
        TagsAction parentTags = startFoo.getAction(TagsAction.class);
        assertNotNull(parentTags);
        assertNotNull(parentTags.getTags());
        assertFalse(parentTags.getTags().isEmpty());
        assertTrue(parentTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(Utils.getStageStatusMetadata().getFailedAndContinued(),
                parentTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

    }

    @Test
    public void executionModelAction() throws Exception {
        WorkflowRun b = expect("executionModelAction").go();

        ExecutionModelAction action = b.getAction(ExecutionModelAction.class);
        assertNotNull(action);
        ModelASTStages stages = action.getStages();
        assertExecutionModelActionStageContents(b, stages);
    }

    @Test
    public void executionModelActionFullPipeline() throws Exception {
        WorkflowRun b = expect("executionModelAction").go();

        ExecutionModelAction action = b.getAction(ExecutionModelAction.class);
        assertNotNull(action);
        ModelASTPipelineDef pipeline = action.getPipelineDef();
        assertNull(pipeline.getSourceLocation());

        ModelASTAgent agent = pipeline.getAgent();
        assertNotNull(agent);
        assertEquals("none", agent.getAgentType().getKey());

        ModelASTEnvironment env = pipeline.getEnvironment();
        assertNotNull(env);
        ModelASTKey var = new ModelASTKey(null);
        var.setKey("VAR");
        ModelASTValue val = (ModelASTValue) env.getVariables().get(var);
        assertNotNull(val);
        assertTrue(val.isLiteral());
        assertEquals("VALUE", val.getValue());

        ModelASTStages stages = pipeline.getStages();
        assertExecutionModelActionStageContents(b, stages);
    }

    private void assertExecutionModelActionStageContents(WorkflowRun b, ModelASTStages stages) throws Exception {
        assertNotNull(stages);
        assertNull(stages.getSourceLocation());

        assertEquals(1, stages.getStages().size());

        ModelASTStage stage = stages.getStages().get(0);
        assertNotNull(stage);
        assertNull(stage.getSourceLocation());

        assertEquals(2, stage.getBranches().size());

        ModelASTBranch firstBranch = branchForName("first", stage.getBranches());
        assertNotNull(firstBranch);
        assertNull(firstBranch.getSourceLocation());
        assertNotNull(firstBranch);
        assertEquals(1, firstBranch.getSteps().size());
        ModelASTStep firstStep = firstBranch.getSteps().get(0);
        assertNull(firstStep.getSourceLocation());
        assertEquals("echo", firstStep.getName());
        ModelASTValue val = null;
        if (firstStep.getArgs() instanceof ModelASTSingleArgument) {
            val = ((ModelASTSingleArgument) firstStep.getArgs()).getValue();
        } else if (firstStep.getArgs() instanceof ModelASTNamedArgumentList && ((ModelASTNamedArgumentList) firstStep.getArgs()).getArguments().size() == 1) {
            val = ((ModelASTNamedArgumentList) firstStep.getArgs()).valueForName("message");
        }
        assertNotNull(val);
        assertEquals("First branch", val.getValue());

        assertNull(firstStep.getArgs().getSourceLocation());
        assertNull(val.getSourceLocation());

        ModelASTBranch secondBranch = branchForName("second", stage.getBranches());
        assertNotNull(secondBranch);
        assertNull(secondBranch.getSourceLocation());
        assertEquals(2, secondBranch.getSteps().size());
        ModelASTStep scriptStep = secondBranch.getSteps().get(0);
        assertNull(scriptStep.getSourceLocation());
        assertTrue(scriptStep instanceof ModelASTScriptBlock);
        assertNull(scriptStep.getArgs().getSourceLocation());
        ModelASTValue scriptVal = null;
        if (scriptStep.getArgs() instanceof ModelASTSingleArgument) {
            scriptVal = ((ModelASTSingleArgument) scriptStep.getArgs()).getValue();
        } else if (scriptStep.getArgs() instanceof ModelASTNamedArgumentList && ((ModelASTNamedArgumentList) scriptStep.getArgs()).getArguments().size() == 1) {
            scriptVal = ((ModelASTNamedArgumentList) scriptStep.getArgs()).valueForName("scriptBlock");
        }
        assertNotNull(scriptVal);
        assertNull(scriptVal.getSourceLocation());

        ModelASTStep timeoutStep = secondBranch.getSteps().get(1);
        assertNull(timeoutStep.getSourceLocation());
        assertTrue(timeoutStep instanceof ModelASTTreeStep);
        assertEquals("timeout", timeoutStep.getName());

        ModelASTTreeStep treeStep = (ModelASTTreeStep)timeoutStep;
        assertEquals(1, treeStep.getChildren().size());
        assertEquals("echo", treeStep.getChildren().get(0).getName());
        assertNull(treeStep.getChildren().get(0).getSourceLocation());

        j.assertLogContains("[Pipeline] { (foo)", b);
        j.assertLogContains("{ (Branch: first)", b);
        j.assertLogContains("{ (Branch: second)", b);
    }

    private ModelASTBranch branchForName(String name, List<ModelASTBranch> branches) {
        for (ModelASTBranch branch : branches) {
            if (branch.getName().equals(name)) {
                return branch;
            }
        }

        return null;
    }

    @Test
    public void syntheticStages() throws Exception {
        WorkflowRun b = expect("syntheticStages")
                .logContains("[Pipeline] { (" + SyntheticStageNames.toolInstall() + ")",
                        "[Pipeline] { (" + SyntheticStageNames.checkout() + ")",
                        "[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
                        "I AM A POST-BUILD")
                .go();

        FlowExecution execution = b.getExecution();

        assertNotNull(execution);
        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.toolInstall(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.checkout(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.postBuild(), Utils.getSyntheticStageMetadata().getPost())));
        assertNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.agentSetup(), Utils.getSyntheticStageMetadata().getPre())));
    }

    @Test
    public void noToolSyntheticStage() throws Exception {
        WorkflowRun b = expect("noToolSyntheticStage")
                .logContains("[Pipeline] { (" + SyntheticStageNames.checkout() + ")",
                        "[Pipeline] { (foo)",
                        "hello",
                        "[Pipeline] { (" + SyntheticStageNames.postBuild() + ")",
                        "I AM A POST-BUILD")
                .go();

        FlowExecution execution = b.getExecution();

        assertNotNull(execution);
        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        assertNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.toolInstall(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.checkout(), Utils.getSyntheticStageMetadata().getPre())));
        assertNotNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.postBuild(), Utils.getSyntheticStageMetadata().getPost())));
        assertNull(scanner.findFirstMatch(heads, null, syntheticStagePredicate(SyntheticStageNames.agentSetup(), Utils.getSyntheticStageMetadata().getPre())));
    }

    @Test
    public void skippedStagesForFailure() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "skippedStagesForFailure")
                .logContains("[Pipeline] { (foo)", "hello", "I have failed")
                .logNotContains("I will be skipped", "I also will be skipped", "I have succeeded")
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        assertNotNull(execution.getCauseOfFailure());

        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        assertNull(scanner.findFirstMatch(heads, stageStatusPredicate("foo", Utils.getStageStatusMetadata().getSkippedForFailure())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("foo", Utils.getStageStatusMetadata().getFailedAndContinued())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("bar", Utils.getStageStatusMetadata().getSkippedForFailure())));
        assertNotNull(scanner.findFirstMatch(heads, stageStatusPredicate("baz", Utils.getStageStatusMetadata().getSkippedForFailure())));
    }

    @Test
    public void skippedStagesInParallel() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "skippedStagesInParallel")
                .logContains("[Pipeline] { (foo)", "hello", "I will not be skipped but I will fail")
                .logNotContains("I will be skipped", "skipped for earlier failure", "I have succeeded")
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        assertNotNull(execution.getCauseOfFailure());

        // foo didn't fail at all.
        List<FlowNode> fooStages = Utils.findStageFlowNodes("foo", execution);
        assertNotNull(fooStages);
        assertEquals(1, fooStages.size());
        FlowNode foo = fooStages.get(0);
        assertFalse(stageStatusPredicate("foo", StageStatus.getSkippedForFailure()).apply(foo));
        assertFalse(stageStatusPredicate("foo", StageStatus.getFailedAndContinued()).apply(foo));

        // first-parallel did fail.
        List<FlowNode> firstParallelStages = Utils.findStageFlowNodes("first-parallel", execution);
        assertNotNull(firstParallelStages);
        assertEquals(1, firstParallelStages.size());
        FlowNode firstParallel = firstParallelStages.get(0);
        assertFalse(stageStatusPredicate("first-parallel", StageStatus.getSkippedForFailure()).apply(firstParallel));
        assertTrue(stageStatusPredicate("first-parallel", StageStatus.getFailedAndContinued()).apply(firstParallel));

        // bar was a parallel stage that was skipped due to conditional.
        List<FlowNode> barStages = Utils.findStageFlowNodes("bar", execution);
        assertNotNull(barStages);
        assertEquals(2, barStages.size());

        for (FlowNode bar : barStages) {
            assertTrue(stageStatusPredicate("bar", StageStatus.getSkippedForConditional()).apply(bar));
        }

        // baz was a parallel stage that failed.
        List<FlowNode> bazStages = Utils.findStageFlowNodes("baz", execution);
        assertNotNull(bazStages);
        assertEquals(2, bazStages.size());

        for (FlowNode baz : bazStages) {
            assertTrue(stageStatusPredicate("baz", StageStatus.getFailedAndContinued()).apply(baz));
        }

        // second-parallel should be skipped for failure.
        List<FlowNode> secondParallelStages = Utils.findStageFlowNodes("second-parallel", execution);
        assertNotNull(secondParallelStages);
        assertEquals(1, secondParallelStages.size());
        FlowNode secondParallel = secondParallelStages.get(0);
        assertTrue(stageStatusPredicate("second-parallel", StageStatus.getSkippedForFailure()).apply(secondParallel));

        // bar2 was a parallel stage skipped for failure
        List<FlowNode> bar2Stages = Utils.findStageFlowNodes("bar2", execution);
        assertNotNull(bar2Stages);
        assertEquals(2, bar2Stages.size());

        for (FlowNode bar2 : bar2Stages) {
            assertTrue(stageStatusPredicate("bar2", StageStatus.getSkippedForFailure()).apply(bar2));
        }

        // baz2 was a parallel stage skipped for failure
        List<FlowNode> baz2Stages = Utils.findStageFlowNodes("baz2", execution);
        assertNotNull(baz2Stages);
        assertEquals(2, baz2Stages.size());

        for (FlowNode baz2 : baz2Stages) {
            assertTrue(stageStatusPredicate("baz2", StageStatus.getSkippedForFailure()).apply(baz2));
        }
    }

    public static Predicate<FlowNode> syntheticStagePredicate(String stageName,
                                                        String context) {
        return stageTagPredicate(stageName, Utils.getSyntheticStageMetadata().getTagName(), context);
    }

    public static Predicate<FlowNode> stageStatusPredicate(String stageName,
                                                     String stageStatus) {
        return stageTagPredicate(stageName, Utils.getStageStatusMetadata().getTagName(), stageStatus);
    }

    public static Predicate<FlowNode> stageTagPredicate(final String stageName,
                                                  final String tagName,
                                                  final String tagValue) {
        return new Predicate<FlowNode>() {
            @Override
            public boolean apply(FlowNode input) {
                ThreadNameAction threadNameAction = input.getAction(ThreadNameAction.class);
                TagsAction tagsAction = input.getAction(TagsAction.class);
                if (input.getDisplayName().equals(stageName) ||
                        (threadNameAction != null && threadNameAction.getThreadName().equals(stageName))) {
                    if (tagsAction != null) {
                        String realTagVal = tagsAction.getTagValue(tagName);
                        if (realTagVal != null && realTagVal.equals(tagValue)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    @Issue("JENKINS-40188")
    @Test
    public void booleanParamBuildStep() throws Exception {
        env(s).set();
        expect("booleanParamBuildStep")
                .logContains("[Pipeline] { (promote)", "Scheduling project")
                .go();
    }

    @Ignore("No longer relevant due to https://github.com/jenkinsci/workflow-support-plugin/commit/d5d1f46255b623587198a25f8c179c64f0b74d12")
    @Issue("JENKINS-46112")
    @Test
    public void logActionPresentForError() throws Exception {
        WorkflowRun r = expect(Result.FAILURE, "logActionPresentForError").go();
        FlowExecution execution = r.getExecution();
        assertNotNull(execution);
        Collection<FlowNode> heads = execution.getCurrentHeads();

        DepthFirstScanner scanner = new DepthFirstScanner();

        FlowNode n = scanner.findFirstMatch(heads, null, new Predicate<FlowNode>() {
            @Override
            public boolean apply(FlowNode input) {
                return input instanceof StepAtomNode && ((StepAtomNode) input).getDescriptor() instanceof ErrorStep.DescriptorImpl;
            }
        });
        assertNotNull(n);
        LogAction l = n.getAction(LogAction.class);
        assertNotNull(l);
    }

    @Test
    public void mapCallsWithMethodCallValues() throws Exception {
        WorkflowRun b = expect("mapCallsWithMethodCallValues")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();

        WorkflowJob p = b.getParent();

        BuildDiscarderProperty bdp = p.getProperty(BuildDiscarderProperty.class);
        assertNotNull(bdp);
        BuildDiscarder strategy = bdp.getStrategy();
        assertNotNull(strategy);
        assertEquals(LogRotator.class, strategy.getClass());
        LogRotator lr = (LogRotator) strategy;
        assertEquals(1, lr.getNumToKeep());

    }

    @Test
    public void fromEvaluate() throws Exception {
        expect("fromEvaluate")
                .otherResource("simplePipeline.groovy", "simplePipeline.groovy")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();
    }

    @Issue("JENKINS-47193")
    @Test
    public void classInJenkinsfile() throws Exception {
        expect("basic/classInJenkinsfile")
                .logContains("[Pipeline] { (foo)", "hello")
                .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
                .go();
    }

    @Issue("JENKINS-46252")
    @Test
    public void declarativeJobAction() throws Exception {
        WorkflowRun r1 = expect("simplePipeline").go();
        WorkflowJob j1 = r1.getParent();
        assertNotNull(j1.getAction(DeclarativeJobAction.class));

        WorkflowJob j2 = j.createProject(WorkflowJob.class, "nonDeclarative");
        j2.setDefinition(new CpsFlowDefinition("echo 'hi'", true));
        j.buildAndAssertSuccess(j2);
        assertNull(j2.getAction(DeclarativeJobAction.class));
    }

    @Issue("JENKINS-49070")
    @Test
    public void bigDecimalConverts() throws Exception {
        WorkflowRun b = expect("basic/bigDecimalConverts").go();

        ExecutionModelAction action = b.getAction(ExecutionModelAction.class);
        assertNotNull(action);
        ModelASTStages stages = action.getStages();
        assertNull(stages.getSourceLocation());
        assertNotNull(stages);

        assertEquals(1, stages.getStages().size());

        ModelASTStage stage = stages.getStages().get(0);
        assertNull(stage.getSourceLocation());
        assertNotNull(stage);

        assertEquals(1, stage.getBranches().size());

        ModelASTBranch firstBranch = branchForName("default", stage.getBranches());
        assertNotNull(firstBranch);
        assertNull(firstBranch.getSourceLocation());
        assertNotNull(firstBranch);
        assertEquals(1, firstBranch.getSteps().size());
        ModelASTStep firstStep = firstBranch.getSteps().get(0);
        assertNull(firstStep.getSourceLocation());
        assertEquals("junit", firstStep.getName());
        assertTrue(firstStep.getArgs() instanceof ModelASTNamedArgumentList);
        ModelASTNamedArgumentList args = (ModelASTNamedArgumentList)firstStep.getArgs();

        ModelASTValue val = null;
        for (ModelASTKey k : args.getArguments().keySet()) {
            if (k.getKey().equals("healthScaleFactor")) {
                val = args.getArguments().get(k);
            }
        }

        assertNotNull(val);

        Object realVal = val.getValue();

        assertEquals(new Double("1.0"), realVal);

    }

    @Issue("JENKINS-46809")
    @Test
    public void topLevelStageGroup() throws Exception {
        expect("basic/topLevelStageGroup")
                .logContains("[Pipeline] { (foo)",
                        "In stage bar in group foo",
                        "In stage baz in group foo")
                .go();
    }

    @Issue("JENKINS-60115")
    @Test
    public void singleArgumentNullValue() throws Exception {
        expect("basic/singleArgumentNullValue")
            .logContains("[Pipeline] { (foo)",
                "Trying to pass milestone 0",
                "Null is no problem")
            .go();
    }

    @Issue("JENKINS-51962")
    @Test
    public void failureInFirstOfSequential() throws Exception {
        expect(Result.FAILURE, "basic/failureInFirstOfSequential")
                .logNotContains("Executing stage B")
                .go();
    }

    @Issue("JENKINS-49135")
    @Test
    public void userCanTransformAST() throws Exception {
        initGlobalLibrary();
        expect("basic/userCanTransformAST")
                .logContains("[Pipeline] { (foo)",
                        "I got here by AST transformation")
                .go();
    }
}
