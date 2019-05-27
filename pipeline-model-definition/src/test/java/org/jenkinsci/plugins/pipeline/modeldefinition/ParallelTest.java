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
import com.google.common.base.Predicate;
import htmlpublisher.HtmlPublisherTarget;
import hudson.model.Cause.UserIdCause;
import hudson.model.CauseAction;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.LogRotator;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.triggers.TimerTrigger.TimerTriggerCause;
import jenkins.model.BuildDiscarder;
import jenkins.model.BuildDiscarderProperty;
import jenkins.plugins.git.GitSCMSource;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKey;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTScriptBlock;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.jenkinsci.plugins.workflow.steps.ErrorStep;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Andrew Bayer
 */
public class ParallelTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setNumExecutors(10);
        s.setLabelString("some-label docker");
    }

    @Issue("JENKINS-41334")
    @Test
    public void parallelStagesHaveStatusAndPost() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "parallel/parallelStagesHaveStatusAndPost")
                .logContains("[Pipeline] { (foo)",
                        "{ (Branch: first)",
                        "[Pipeline] { (first)",
                        "{ (Branch: second)",
                        "[Pipeline] { (second)",
                        "FIRST BRANCH FAILED",
                        "SECOND BRANCH POST",
                        "FOO STAGE FAILED")
                .hasFailureCase()
                .go();

        FlowExecution execution = b.getExecution();
        assertNotNull(execution);
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof BlockStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNotNull(endFoo.getError());

        FlowNode startFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("first"));
        assertNotNull(startFirst);
        assertTrue(startFirst instanceof BlockStartNode);
        FlowNode endFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFirst));
        assertNotNull(endFirst);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));
        assertNotNull(endFirst.getError());

        FlowNode startThird = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("third"));
        assertNotNull(startThird);
        assertTrue(startThird instanceof BlockStartNode);
        FlowNode endThird = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startThird));
        assertNotNull(endThird);
        assertEquals(GenericStatus.NOT_EXECUTED, StatusAndTiming.computeChunkStatus(b, null, startThird, endThird, null));

        TagsAction thirdTags = startThird.getAction(TagsAction.class);
        assertNotNull(thirdTags);
        assertNotNull(thirdTags.getTags());
        assertFalse(thirdTags.getTags().isEmpty());
        assertTrue(thirdTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(StageStatus.getSkippedForConditional(),
                thirdTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        TagsAction nestedTags = startFirst.getAction(TagsAction.class);
        assertNotNull(nestedTags);
        assertNotNull(nestedTags.getTags());
        assertFalse(nestedTags.getTags().isEmpty());
        assertTrue(nestedTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(StageStatus.getFailedAndContinued(),
                nestedTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        TagsAction parentTags = startFoo.getAction(TagsAction.class);
        assertNotNull(parentTags);
        assertNotNull(parentTags.getTags());
        assertFalse(parentTags.getTags().isEmpty());
        assertTrue(parentTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
        assertEquals(StageStatus.getFailedAndContinued(),
                parentTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

        // Was originally testing to see if the last-but-one node in the failed block was the failure but that's
        // actually a bogus test, particularly when running post stuff.
    }

    @Test
    public void parallelPipeline() throws Exception {
        expect("parallel/parallelPipeline")
                .logContains("[Pipeline] { (foo)", "{ (Branch: first)", "{ (Branch: second)")
                .go();
    }

    @Test
    public void parallelPipelineQuoteEscaping() throws Exception {
        expect("parallel/parallelPipelineQuoteEscaping")
                .logContains("[Pipeline] { (foo)", "{ (Branch: first)", "{ (Branch: \"second\")")
                .go();
    }

    @Test
    public void parallelPipelineWithSpaceInBranch() throws Exception {
        expect("parallel/parallelPipelineWithSpaceInBranch")
                .logContains("[Pipeline] { (foo)", "{ (Branch: first one)", "{ (Branch: second one)")
                .go();
    }

    @Test
    public void parallelPipelineWithFailFast() throws Exception {
        expect("parallel/parallelPipelineWithFailFast")
                .logContains("[Pipeline] { (foo)", "{ (Branch: first)", "{ (Branch: second)")
                .go();
    }

    @Issue("JENKINS-43625")
    @Test
    public void parallelAndPostFailure() throws Exception {
        expect(Result.FAILURE, "parallel/parallelAndPostFailure")
                .logContains("[Pipeline] { (foo)", "I HAVE EXPLODED")
                .logNotContains("{ (Branch: first)", "{ (Branch: second)")
                .go();
    }

    @Issue("JENKINS-41334")
    @Test
    public void nestedParallelStages() throws Exception {
        expect("parallel/nestedParallelStages")
                .logContains("[Pipeline] { (foo)", "{ (Branch: first)", "{ (Branch: second)")
                .go();
    }

    @Issue("JENKINS-41334")
    @Test
    public void parallelStagesAgentEnvWhen() throws Exception {
        Slave s = j.createOnlineSlave();
        s.setLabelString("first-agent");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first agent")));

        Slave s2 = j.createOnlineSlave();
        s2.setLabelString("second-agent");
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second agent")));

        expect("parallel/parallelStagesAgentEnvWhen")
                .logContains("[Pipeline] { (foo)",
                        "{ (Branch: first)",
                        "{ (Branch: second)",
                        "First stage, first agent",
                        "First stage, do not override",
                        "First stage, overrode once and done",
                        "First stage, overrode twice, in first branch",
                        "First stage, overrode per nested, in first branch",
                        "First stage, declared per nested, in first branch",
                        "Second stage, second agent",
                        "Second stage, do not override",
                        "Second stage, overrode once and done",
                        "Second stage, overrode twice, in second branch",
                        "Second stage, overrode per nested, in second branch",
                        "Second stage, declared per nested, in second branch",
                        "Apache Maven 3.0.1")
                .logNotContains("WE SHOULD NEVER GET HERE")
                .go();
    }

    @Issue("JENKINS-46809")
    @Test
    public void parallelStagesGroupsAndStages() throws Exception {
        Slave s = j.createOnlineSlave();
        s.setLabelString("first-agent");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first agent")));

        Slave s2 = j.createOnlineSlave();
        s2.setLabelString("second-agent");
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second agent")));

        WorkflowRun b = expect("parallel/parallelStagesGroupsAndStages")
                .logContains("[Pipeline] { (foo)",
                        "{ (Branch: first)",
                        "{ (Branch: second)",
                        "First stage, first agent",
                        "[Pipeline] { (inner-first)",
                        "Second stage, second agent",
                        "Apache Maven 3.0.1",
                        "[Pipeline] { (inner-second)")
                .logNotContains("WE SHOULD NEVER GET HERE")
                .go();

        FlowExecution execution = b.getExecution();
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof BlockStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNull(endFoo.getError());

        FlowNode startFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("first"));
        assertNotNull(startFirst);
        assertTrue(startFirst instanceof BlockStartNode);
        FlowNode endFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFirst));
        assertNotNull(endFirst);
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));

        FlowNode startInnerFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("inner-first"));
        assertNotNull(startInnerFirst);
        assertTrue(startInnerFirst instanceof BlockStartNode);
        FlowNode endInnerFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startInnerFirst));
        assertNotNull(endInnerFirst);
        assertEquals(GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(b, null, startInnerFirst, endInnerFirst, null));

        FlowNode startInnerSecond = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("inner-second"));
        assertNotNull(startInnerSecond);
        assertTrue(startInnerSecond instanceof BlockStartNode);
        FlowNode endInnerSecond = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startInnerSecond));
        assertNotNull(endInnerSecond);
        assertEquals(GenericStatus.NOT_EXECUTED, StatusAndTiming.computeChunkStatus(b, null, startInnerSecond, endInnerSecond, null));

        assertTrue(StageStatus.isSkippedStageForReason(startInnerSecond, StageStatus.getSkippedForConditional()));

        /*
        Relevant FlowNode ids:
        - 2:  FlowStartNode
        - 3:  foo stage start
        - 4:  foo stage body
        - 5:  parallel start
        - 7:  first branch start
        - 8:  second branch start
        - 9:  first stage start
        - 11: second stage start
        - 12: second stage body
        - 14: inner-first stage start (probably)
        - 44: inner-second stage start (probably)

        All three parallel stages should share 5,4,3,2.
        Sequential stages in the second branch should share 8,5,4,3,2.
         */
        assertEquals(Arrays.asList("7", "5", "4", "3", "2"), tailOfList(startFirst.getAllEnclosingIds()));
        assertEquals(Arrays.asList("12", "11", "8", "5", "4", "3", "2"), tailOfList(startInnerFirst.getAllEnclosingIds()));
        assertEquals(Arrays.asList("12", "11", "8", "5", "4", "3", "2"), tailOfList(startInnerSecond.getAllEnclosingIds()));
    }

    @Issue("JENKINS-53734")
    @Test
    public void parallelStagesNestedInSequential() throws Exception {
        Slave s = j.createOnlineSlave();
        s.setLabelString("first-agent");
        s.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first agent")));

        Slave s2 = j.createOnlineSlave();
        s2.setLabelString("second-agent");
        s2.getNodeProperties().add(new EnvironmentVariablesNodeProperty(new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second agent")));

        expect("parallel/parallelStagesNestedInSequential")
                .logContains("[Pipeline] { (foo)",
                        "First stage, first agent",
                        "[Pipeline] { (inner-first)",
                        "Second stage, second agent",
                        "Apache Maven 3.0.1",
                        "[Pipeline] { (inner-second)")
                .logNotContains("WE SHOULD NEVER GET HERE")
                .go();
    }

    private List<String> tailOfList(List<String> l) {
        return Collections.unmodifiableList(l.subList(1, l.size()));
    }

    @Issue(value = {"JENKINS-47109", "JENKINS-55459"})
    @Test
    public void parallelStagesFailFast() throws Exception {
        expect(Result.FAILURE, "parallel/parallelStagesFailFast")
                .logContains("[Pipeline] { (foo)",
                        "{ (Branch: first)",
                        "[Pipeline] { (first)",
                        "{ (Branch: second)",
                        "[Pipeline] { (second)",
                        "FIRST STAGE FAILURE",
                        "SECOND STAGE ABORTED")
                .logNotContains("Second branch",
                        "FIRST STAGE ABORTED",
                        "SECOND STAGE FAILURE")
                .hasFailureCase()
                .go();
    }

    @Issue(value = {"JENKINS-53558", "JENKINS-55459"})
    @Test
    public void parallelStagesFailFastWithOption() throws Exception {
        expect(Result.FAILURE,"parallel/parallelStagesFailFastWithOption")
                .logContains("[Pipeline] { (foo)",
                        "{ (Branch: first)",
                        "[Pipeline] { (first)",
                        "{ (Branch: second)",
                        "[Pipeline] { (second)",
                        "FIRST STAGE FAILURE",
                        "SECOND STAGE ABORTED")
                .logNotContains("Second branch",
                        "FIRST STAGE ABORTED",
                        "SECOND STAGE FAILURE")
                .hasFailureCase()
                .go();

    }

    @Issue(value = {"JENKINS-55459", "JENKINS-56544"})
    @Test
    public void parallelStagesFailFastWithAgent() throws Exception {
        expect(Result.FAILURE, "parallel/parallelStagesFailFastWithAgent")
                .logContains("[Pipeline] { (foo)",
                        "{ (Branch: first)",
                        "[Pipeline] { (first)",
                        "{ (Branch: second)",
                        "[Pipeline] { (second)",
                        "FIRST STAGE FAILURE",
                        "SECOND STAGE ABORTED")
                .logNotContains("Second branch",
                        "FIRST STAGE ABORTED",
                        "SECOND STAGE FAILURE")
                .hasFailureCase()
                .go();
    }

    @Issue("JENKINS-47783")
    @Test
    public void parallelStagesHaveStatusWhenSkipped() throws Exception {
        WorkflowRun b = expect(Result.FAILURE, "parallel/parallelStagesHaveStatusWhenSkipped")
                .logContains("[Pipeline] { (bar)",
                        "[Pipeline] { (foo)",
                        "{ (Branch: first)",
                        "[Pipeline] { (first)",
                        "{ (Branch: second)",
                        "[Pipeline] { (second)")
                .hasFailureCase()
                .go();

        FlowExecution execution = b.getExecution();
        List<FlowNode> heads = execution.getCurrentHeads();
        DepthFirstScanner scanner = new DepthFirstScanner();
        FlowNode startFoo = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("foo"));
        assertNotNull(startFoo);
        assertTrue(startFoo instanceof BlockStartNode);
        FlowNode endFoo = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFoo));
        assertNotNull(endFoo);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
        assertNotNull(endFoo.getError());

        FlowNode startFirst = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("first"));
        assertNotNull(startFirst);
        assertTrue(startFirst instanceof BlockStartNode);
        FlowNode endFirst = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startFirst));
        assertNotNull(endFirst);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));

        FlowNode startSecond = scanner.findFirstMatch(heads, null, Utils.isStageWithOptionalName("second"));
        assertNotNull(startSecond);
        assertTrue(startSecond instanceof BlockStartNode);
        FlowNode endSecond = scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode)startSecond));
        assertNotNull(endSecond);
        assertEquals(GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startSecond, endSecond, null));

        assertTrue(StageStatus.isSkippedStageForReason(startFirst, StageStatus.getSkippedForFailure()));
        assertTrue(StageStatus.isSkippedStageForReason(startSecond, StageStatus.getSkippedForFailure()));
        assertTrue(StageStatus.isSkippedStageForReason(startFirst, StageStatus.getSkippedForFailure()));
        }

    @Issue("JENKINS-46597")
    @Test
    public void parallelStagesShoudntTriggerNSE() throws Exception {
        expect("parallel/parallelStagesShouldntTriggerNSE")
                .logContains("ninth branch")
                .go();
    }
}
