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

import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.pipeline.StageStatus;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.junit.*;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/** @author Andrew Bayer */
public class MatrixTest extends AbstractModelDefTest {

  private static Slave s;
  private static Slave linux;
  private static Slave mac;
  private static Slave windows;

  private static String password;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setLabelString("agent-one some-label");
    s.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first")));
    s.setNumExecutors(10);

    linux = j.createOnlineSlave();
    linux.setLabelString("linux-agent");
    linux
        .getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "linux agent")));
    mac = j.createOnlineSlave();
    mac.setLabelString("mac-agent");
    mac.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "mac agent")));
    windows = j.createOnlineSlave();
    windows.setLabelString("windows-agent");
    windows
        .getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("ONAGENT", "true"),
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "windows agent")));
  }

  @Ignore
  @Issue("JENKINS-41334")
  @Test
  public void matrixStagesHaveStatusAndPost() throws Exception {
    WorkflowRun b =
        expect(Result.FAILURE, "matrix/matrixStagesHaveStatusAndPost")
            .logContains(
                "[Pipeline] { (foo)",
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
    FlowNode startFoo =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("foo"));
    assertNotNull(startFoo);
    assertTrue(startFoo instanceof BlockStartNode);
    FlowNode endFoo =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startFoo));
    assertNotNull(endFoo);
    assertEquals(
        GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
    assertNotNull(endFoo.getError());

    FlowNode startFirst =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("first"));
    assertNotNull(startFirst);
    assertTrue(startFirst instanceof BlockStartNode);
    FlowNode endFirst =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startFirst));
    assertNotNull(endFirst);
    assertEquals(
        GenericStatus.FAILURE,
        StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));
    assertNotNull(endFirst.getError());

    FlowNode startThird =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("third"));
    assertNotNull(startThird);
    assertTrue(startThird instanceof BlockStartNode);
    FlowNode endThird =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startThird));
    assertNotNull(endThird);
    assertEquals(
        GenericStatus.NOT_EXECUTED,
        StatusAndTiming.computeChunkStatus(b, null, startThird, endThird, null));

    TagsAction thirdTags = startThird.getAction(TagsAction.class);
    assertNotNull(thirdTags);
    assertNotNull(thirdTags.getTags());
    assertFalse(thirdTags.getTags().isEmpty());
    assertTrue(thirdTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
    assertEquals(
        StageStatus.getSkippedForConditional(),
        thirdTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

    TagsAction nestedTags = startFirst.getAction(TagsAction.class);
    assertNotNull(nestedTags);
    assertNotNull(nestedTags.getTags());
    assertFalse(nestedTags.getTags().isEmpty());
    assertTrue(nestedTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
    assertEquals(
        StageStatus.getFailedAndContinued(),
        nestedTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

    TagsAction parentTags = startFoo.getAction(TagsAction.class);
    assertNotNull(parentTags);
    assertNotNull(parentTags.getTags());
    assertFalse(parentTags.getTags().isEmpty());
    assertTrue(parentTags.getTags().containsKey(Utils.getStageStatusMetadata().getTagName()));
    assertEquals(
        StageStatus.getFailedAndContinued(),
        parentTags.getTags().get(Utils.getStageStatusMetadata().getTagName()));

    // Was originally testing to see if the last-but-one node in the failed block was the failure
    // but that's
    // actually a bogus test, particularly when running post stuff.
  }

  @Test
  public void matrixPipeline() throws Exception {
    expect("matrix/matrixPipeline")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')")
        .go();
  }

  @Test
  public void matrixPipelineLiterals() throws Exception {
    expect("matrix/matrixPipelineLiterals")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = '1')",
            "{ (Branch: Matrix - OS_VALUE = 'true')")
        .go();
  }

  @Ignore("Too large for ci testing")
  @Issue("JENKINS-37984")
  @Test
  public void matrix50() throws Exception {
    expect("matrix/matrix50")
        .logContains(
            "{ (Branch: Matrix - letters1 = 'a', letters10 = 'a')",
            "{ (Branch: Matrix - letters1 = 'j', letters10 = 'e')")
        .go();
  }

  @Issue("JENKINS-37984")
  @Test
  public void matrix100() throws Exception {
    expect("matrix/matrix100")
        .logContains(
            "{ (Branch: Matrix - letters1 = 'a', letters10 = 'a')",
            "{ (Branch: Matrix - letters1 = 'j', letters10 = 'j')")
        .go();
  }

  @Ignore("Too large for ci testing")
  @Issue("JENKINS-47363")
  @Test
  public void matrix300() throws Exception {
    expect("matrix/matrix300")
        .logContains(
            "{ (Branch: Matrix - letters1 = 'a', letters10 = 'a', letters100 = 'a')",
            "{ (Branch: Matrix - letters1 = 'j', letters10 = 'j', letters100 = 'c')")
        .go();
  }

  @Ignore("Too large for ci testing")
  @Issue("JENKINS-37984")
  @Test
  public void matrix1024() throws Exception {
    expect("matrix/matrix1024")
        .logContains(
            "{ (Branch: Matrix - letters1 = 'a', letters4 = 'a', letters16 = 'a', letters64 = 'a', letters256 = 'a')",
            "{ (Branch: Matrix - letters1 = 'd', letters4 = 'd', letters16 = 'd', letters64 = 'd', letters256 = 'd')")
        .go();
  }

  @Test
  public void matrixPipelineTwoAxis() throws Exception {
    expect("matrix/matrixPipelineTwoAxis")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'safari')")
        .go();
  }

  @Ignore("Scenario covered by matrixPipelineTwoAxisExcludeNot ")
  @Test
  public void matrixPipelineTwoAxisOneExclude() throws Exception {
    expect("matrix/matrixPipelineTwoAxisOneExclude")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'safari')")
        .logNotContains("{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'safari')")
        .go();
  }

  @Ignore("Scenario covered by matrixPipelineTwoAxisExcludeNot ")
  @Test
  public void matrixPipelineTwoAxisTwoExcludes() throws Exception {
    expect("matrix/matrixPipelineTwoAxisTwoExcludes")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'ie')")
        .logNotContains(
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'ie')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'ie')")
        .go();
  }

  @Test
  public void matrixPipelineTwoAxisExcludeNot() throws Exception {
    expect("matrix/matrixPipelineTwoAxisExcludeNot")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'firefox')",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'chrome')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'windows', BROWSER_VALUE = 'ie')")
        .logNotContains(
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'safari')",
            "{ (Branch: Matrix - OS_VALUE = 'linux', BROWSER_VALUE = 'ie')",
            "{ (Branch: Matrix - OS_VALUE = 'mac', BROWSER_VALUE = 'ie')")
        .go();
  }

  @Ignore
  @Test
  public void matrixPipelineQuoteEscaping() throws Exception {
    expect("matrix/matrixPipelineQuoteEscaping")
        .logContains("[Pipeline] { (foo)", "{ (Branch: first)", "{ (Branch: \"second\")")
        .go();
  }

  @Issue("JENKINS-41334")
  @Test
  public void matrixStageDirectives() throws Exception {
    // ensure matrix still works with splitting transform turned off
    RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION = false;
    expect("matrix/matrixStageDirectives")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "First stage, mac agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first mac-os branch",
            "First stage, overrode per nested, in first mac-os branch",
            "First stage, declared per nested, in first mac-os branch",
            "First stage, windows agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first windows-os branch",
            "First stage, overrode per nested, in first windows-os branch",
            "First stage, declared per nested, in first windows-os branch",
            "First stage, linux agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first linux-os branch",
            "First stage, overrode per nested, in first linux-os branch",
            "First stage, declared per nested, in first linux-os branch",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1")
        .logNotContains(
            "WE SHOULD NEVER GET HERE",
            "java.lang.IllegalArgumentException",
            "override in matrix axis")
        .go();
  }

  // Behavior should be identical to previous, just expressed differently
  @Issue("JENKINS-41334")
  @Test
  public void matrixStageDirectivesChildStage() throws Exception {
    expect("matrix/matrixStageDirectivesChildStage")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "First stage, mac agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first mac-os branch",
            "First stage, overrode per nested, in first mac-os branch",
            "First stage, declared per nested, in first mac-os branch",
            "First stage, windows agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first windows-os branch",
            "First stage, overrode per nested, in first windows-os branch",
            "First stage, declared per nested, in first windows-os branch",
            "First stage, linux agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first linux-os branch",
            "First stage, overrode per nested, in first linux-os branch",
            "First stage, declared per nested, in first linux-os branch",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1")
        .logNotContains(
            "WE SHOULD NEVER GET HERE",
            "java.lang.IllegalArgumentException",
            "override in matrix axis")
        .go();
  }

  @Issue("JENKINS-64846")
  @Test
  public void matrixStageDirectivesOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    expect("matrix/matrixStageDirectivesOutsideVarAndFunc")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "First stage, mac agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first mac-os branch",
            "First stage, overrode per nested, in first mac-os branch",
            "First stage, declared per nested, in first mac-os branch",
            "First stage, Hi there - This comes from a function",
            "First stage, windows agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first windows-os branch",
            "First stage, overrode per nested, in first windows-os branch",
            "First stage, declared per nested, in first windows-os branch",
            "First stage, Hi there - This comes from a function",
            "First stage, linux agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first linux-os branch",
            "First stage, overrode per nested, in first linux-os branch",
            "First stage, declared per nested, in first linux-os branch",
            "First stage, Hi there - This comes from a function",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1")
        .logNotContains(
            "WE SHOULD NEVER GET HERE",
            "java.lang.IllegalArgumentException",
            "override in matrix axis")
        .go();
  }

  @Issue("JENKINS-64846")
  @Test
  public void matrixStageDirectivesOutsideVarAndFuncNoSplitting() throws Exception {
    // ensure vars in matrix still works with splitting transform turned off
    RuntimeASTTransformer.SCRIPT_SPLITTING_TRANSFORMATION = false;

    expect("matrix/matrixStageDirectivesOutsideVarAndFunc")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "First stage, mac agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first mac-os branch",
            "First stage, overrode per nested, in first mac-os branch",
            "First stage, declared per nested, in first mac-os branch",
            "First stage, Hi there - This comes from a function",
            "First stage, windows agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first windows-os branch",
            "First stage, overrode per nested, in first windows-os branch",
            "First stage, declared per nested, in first windows-os branch",
            "First stage, Hi there - This comes from a function",
            "First stage, linux agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first linux-os branch",
            "First stage, overrode per nested, in first linux-os branch",
            "First stage, declared per nested, in first linux-os branch",
            "First stage, Hi there - This comes from a function",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1")
        .logNotContains(
            "WE SHOULD NEVER GET HERE",
            "java.lang.IllegalArgumentException",
            "override in matrix axis")
        .go();
  }

  @Issue("JENKINS-41334")
  @Test
  public void matrixStageDirectivesWhenBeforeAgent() throws Exception {
    expect("matrix/matrixStageDirectivesWhenBeforeAgent")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "First stage, mac agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first mac-os branch",
            "First stage, overrode per nested, in first mac-os branch",
            "First stage, declared per nested, in first mac-os branch",
            "First stage, windows agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first windows-os branch",
            "First stage, overrode per nested, in first windows-os branch",
            "First stage, declared per nested, in first windows-os branch",
            "First stage, linux agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first linux-os branch",
            "First stage, overrode per nested, in first linux-os branch",
            "First stage, declared per nested, in first linux-os branch",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1")
        .logNotContains(
            "WE SHOULD NEVER GET HERE",
            "java.lang.IllegalArgumentException",
            "override in matrix axis")
        .go();
  }

  // Behavior should be identical to previous, just expressed differently
  @Issue("JENKINS-41334")
  @Test
  public void matrixStageDirectivesWhenBeforeAgentChildStage() throws Exception {
    expect("matrix/matrixStageDirectivesWhenBeforeAgentChildStage")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "First stage, mac agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first mac-os branch",
            "First stage, overrode per nested, in first mac-os branch",
            "First stage, declared per nested, in first mac-os branch",
            "First stage, windows agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first windows-os branch",
            "First stage, overrode per nested, in first windows-os branch",
            "First stage, declared per nested, in first windows-os branch",
            "First stage, linux agent",
            "First stage, do not override",
            "First stage, overrode once and done",
            "First stage, overrode twice, in first linux-os branch",
            "First stage, overrode per nested, in first linux-os branch",
            "First stage, declared per nested, in first linux-os branch",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1",
            "Apache Maven 3.0.1")
        .logNotContains(
            "WE SHOULD NEVER GET HERE",
            "java.lang.IllegalArgumentException",
            "override in matrix axis")
        .go();
  }

  @Ignore
  @Issue("JENKINS-46809")
  @Test
  public void matrixStagesGroupsAndStages() throws Exception {
    Slave s = j.createOnlineSlave();
    s.setLabelString("first-agent");
    s.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first agent")));

    Slave s2 = j.createOnlineSlave();
    s2.setLabelString("second-agent");
    s2.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second agent")));

    WorkflowRun b =
        expect("matrix/matrixStagesGroupsAndStages")
            .logContains(
                "[Pipeline] { (foo)",
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
    FlowNode startFoo =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("foo"));
    assertNotNull(startFoo);
    assertTrue(startFoo instanceof BlockStartNode);
    FlowNode endFoo =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startFoo));
    assertNotNull(endFoo);
    assertEquals(
        GenericStatus.SUCCESS, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
    assertNull(endFoo.getError());

    FlowNode startFirst =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("first"));
    assertNotNull(startFirst);
    assertTrue(startFirst instanceof BlockStartNode);
    FlowNode endFirst =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startFirst));
    assertNotNull(endFirst);
    assertEquals(
        GenericStatus.SUCCESS,
        StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));

    FlowNode startInnerFirst =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("inner-first"));
    assertNotNull(startInnerFirst);
    assertTrue(startInnerFirst instanceof BlockStartNode);
    FlowNode endInnerFirst =
        scanner.findFirstMatch(
            heads, null, Utils.endNodeForStage((BlockStartNode) startInnerFirst));
    assertNotNull(endInnerFirst);
    assertEquals(
        GenericStatus.SUCCESS,
        StatusAndTiming.computeChunkStatus(b, null, startInnerFirst, endInnerFirst, null));

    FlowNode startInnerSecond =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("inner-second"));
    assertNotNull(startInnerSecond);
    assertTrue(startInnerSecond instanceof BlockStartNode);
    FlowNode endInnerSecond =
        scanner.findFirstMatch(
            heads, null, Utils.endNodeForStage((BlockStartNode) startInnerSecond));
    assertNotNull(endInnerSecond);
    assertEquals(
        GenericStatus.NOT_EXECUTED,
        StatusAndTiming.computeChunkStatus(b, null, startInnerSecond, endInnerSecond, null));

    assertTrue(
        StageStatus.isSkippedStageForReason(
            startInnerSecond, StageStatus.getSkippedForConditional()));

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
    assertEquals(
        Arrays.asList("7", "5", "4", "3", "2"), tailOfList(startFirst.getAllEnclosingIds()));
    assertEquals(
        Arrays.asList("12", "11", "8", "5", "4", "3", "2"),
        tailOfList(startInnerFirst.getAllEnclosingIds()));
    assertEquals(
        Arrays.asList("12", "11", "8", "5", "4", "3", "2"),
        tailOfList(startInnerSecond.getAllEnclosingIds()));
  }

  @Ignore
  @Issue("JENKINS-53734")
  @Test
  public void matrixStagesNestedInSequential() throws Exception {
    Slave s = j.createOnlineSlave();
    s.setLabelString("first-agent");
    s.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "first agent")));

    Slave s2 = j.createOnlineSlave();
    s2.setLabelString("second-agent");
    s2.getNodeProperties()
        .add(
            new EnvironmentVariablesNodeProperty(
                new EnvironmentVariablesNodeProperty.Entry("WHICH_AGENT", "second agent")));

    expect("matrix/matrixStagesNestedInSequential")
        .logContains(
            "[Pipeline] { (foo)",
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
  public void matrixStagesFailFast() throws Exception {
    expect(Result.FAILURE, "matrix/matrixStagesFailFast")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "[Pipeline] { (first)",
            "[Pipeline] { (first)",
            "[Pipeline] { (first)",
            "[Pipeline] { (second)",
            "[Pipeline] { (second)",
            "FIRST windows STAGE FAILURE",
            "Failed in branch Matrix - OS_VALUE = 'windows'",
            "SECOND linux STAGE ABORTED",
            "SECOND mac STAGE ABORTED")
        .logNotContains("Second branch", "FIRST STAGE ABORTED", "SECOND STAGE FAILURE")
        .hasFailureCase()
        .go();
  }

  @Issue(value = {"JENKINS-53558", "JENKINS-55459"})
  @Test
  public void matrixStagesFailFastWithOption() throws Exception {
    expect(Result.FAILURE, "matrix/matrixStagesFailFastWithOption")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "[Pipeline] { (first)",
            "[Pipeline] { (first)",
            "[Pipeline] { (first)",
            "[Pipeline] { (second)",
            "[Pipeline] { (second)",
            "FIRST windows STAGE FAILURE",
            "Failed in branch Matrix - OS_VALUE = 'windows'",
            "SECOND linux STAGE ABORTED",
            "SECOND mac STAGE ABORTED")
        .logNotContains("Second branch", "FIRST STAGE ABORTED", "SECOND STAGE FAILURE")
        .hasFailureCase()
        .go();
  }

  @Issue(value = {"JENKINS-55459", "JENKINS-56544"})
  @Test
  public void matrixStagesFailFastWithAgent() throws Exception {
    expect(Result.FAILURE, "matrix/matrixStagesFailFastWithAgent")
        .logContains(
            "[Pipeline] { (foo)",
            "{ (Branch: Matrix - OS_VALUE = 'linux')",
            "{ (Branch: Matrix - OS_VALUE = 'windows')",
            "{ (Branch: Matrix - OS_VALUE = 'mac')",
            "[Pipeline] { (first)",
            "[Pipeline] { (first)",
            "[Pipeline] { (first)",
            "[Pipeline] { (second)",
            "[Pipeline] { (second)",
            "FIRST windows STAGE FAILURE",
            "Failed in branch Matrix - OS_VALUE = 'windows'",
            "SECOND linux STAGE ABORTED",
            "SECOND mac STAGE ABORTED")
        .logNotContains("Second branch", "FIRST STAGE ABORTED", "SECOND STAGE FAILURE")
        .hasFailureCase()
        .go();
  }

  @Issue("JENKINS-47783")
  @Test
  public void matrixStagesHaveStatusWhenSkipped() throws Exception {
    WorkflowRun b =
        expect(Result.FAILURE, "matrix/matrixStagesHaveStatusWhenSkipped")
            .logContains(
                "[Pipeline] { (bar)",
                "[Pipeline] { (foo)",
                "{ (Branch: Matrix - OS_VALUE = 'linux')",
                "{ (Branch: Matrix - OS_VALUE = 'windows')",
                "{ (Branch: Matrix - OS_VALUE = 'mac')",
                "[Pipeline] { (first)",
                "[Pipeline] { (first)",
                "[Pipeline] { (first)",
                "[Pipeline] { (second)",
                "[Pipeline] { (second)",
                "[Pipeline] { (second)")
            .hasFailureCase()
            .go();

    FlowExecution execution = b.getExecution();
    List<FlowNode> heads = execution.getCurrentHeads();
    DepthFirstScanner scanner = new DepthFirstScanner();
    FlowNode startFoo =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("foo"));
    assertNotNull(startFoo);
    assertTrue(startFoo instanceof BlockStartNode);
    FlowNode endFoo =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startFoo));
    assertNotNull(endFoo);
    assertEquals(
        GenericStatus.FAILURE, StatusAndTiming.computeChunkStatus(b, null, startFoo, endFoo, null));
    assertNotNull(endFoo.getError());

    FlowNode startFirst =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("first"));
    assertNotNull(startFirst);
    assertTrue(startFirst instanceof BlockStartNode);
    FlowNode endFirst =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startFirst));
    assertNotNull(endFirst);
    assertEquals(
        GenericStatus.FAILURE,
        StatusAndTiming.computeChunkStatus(b, null, startFirst, endFirst, null));

    FlowNode startSecond =
        scanner.findFirstMatch(heads, null, CommonUtils.isStageWithOptionalName("second"));
    assertNotNull(startSecond);
    assertTrue(startSecond instanceof BlockStartNode);
    FlowNode endSecond =
        scanner.findFirstMatch(heads, null, Utils.endNodeForStage((BlockStartNode) startSecond));
    assertNotNull(endSecond);
    assertEquals(
        GenericStatus.FAILURE,
        StatusAndTiming.computeChunkStatus(b, null, startSecond, endSecond, null));

    assertTrue(StageStatus.isSkippedStageForReason(startFirst, StageStatus.getSkippedForFailure()));
    assertTrue(
        StageStatus.isSkippedStageForReason(startSecond, StageStatus.getSkippedForFailure()));
    assertTrue(StageStatus.isSkippedStageForReason(startFirst, StageStatus.getSkippedForFailure()));
  }

  @Ignore
  @Issue("JENKINS-46597")
  @Test
  public void matrixStagesShouldntTriggerNSE() throws Exception {
    expect("matrix/matrixStagesShouldntTriggerNSE").logContains("ninth branch").go();
  }

  @Test
  public void matrixInput() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "matrixInput");
    p.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("matrix/matrixInput"), true));
    // get the build going, and wait until workflow pauses
    QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
    WorkflowRun b = q.getStartCondition().get();
    CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

    while (b.getAction(InputAction.class) == null) {
      e.waitForSuspension();
    }

    // make sure we are pausing at the right state that reflects what we wrote in the program
    InputAction a = b.getAction(InputAction.class);
    assertEquals(2, a.getExecutions().size());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page;

    InputStepExecution is1 = a.getExecution("Matrix - AXIS_VALUE = 'A'");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(1, a.getExecutions().size());

    is1 = a.getExecution("Matrix - AXIS_VALUE = 'B'");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("One Continues in A", b);
    j.assertLogContains("Two Continues in A", b);
    j.assertLogContains("One Continues in B", b);
    j.assertLogContains("Two Continues in B", b);
  }

  @Test
  public void matrixInputChildStage() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "matrixInputChildStage");
    p.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("matrix/matrixInputChildStage"), true));
    // get the build going, and wait until workflow pauses
    QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
    WorkflowRun b = q.getStartCondition().get();
    CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

    while (b.getAction(InputAction.class) == null) {
      e.waitForSuspension();
    }

    // make sure we are pausing at the right state that reflects what we wrote in the program
    InputAction a = b.getAction(InputAction.class);
    assertEquals(2, a.getExecutions().size());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page;

    InputStepExecution is1 = a.getExecution("Cell");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(1, a.getExecutions().size());

    is1 = a.getExecution("Cell");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("One Continues in A", b);
    j.assertLogContains("Two Continues in A", b);
    j.assertLogContains("One Continues in B", b);
    j.assertLogContains("Two Continues in B", b);
  }

  @Test
  public void matrixInputWhen() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "matrixInputWhen");
    p.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("matrix/matrixInputWhen"), true));
    // get the build going, and wait until workflow pauses
    QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
    WorkflowRun b = q.getStartCondition().get();
    CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

    while (b.getAction(InputAction.class) == null) {
      e.waitForSuspension();
    }

    // make sure we are pausing at the right state that reflects what we wrote in the program
    InputAction a = b.getAction(InputAction.class);
    assertEquals(2, a.getExecutions().size());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page;

    InputStepExecution is1 = a.getExecution("Matrix - AXIS_VALUE = 'A'");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(1, a.getExecutions().size());

    is1 = a.getExecution("Matrix - AXIS_VALUE = 'B'");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("One Continues in A", b);
    j.assertLogContains("Two Continues in A", b);
    j.assertLogContains("Stage \"Matrix - AXIS_VALUE = 'B'\" skipped due to when conditional", b);
    j.assertLogNotContains("One Continues in B", b);
    j.assertLogNotContains("Two Continues in B", b);
  }

  @Test
  public void matrixInputWhenChildStage() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "matrixInputWhenChildStage");
    p.setDefinition(
        new CpsFlowDefinition(
            pipelineSourceFromResources("matrix/matrixInputWhenChildStage"), true));
    // get the build going, and wait until workflow pauses
    QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
    WorkflowRun b = q.getStartCondition().get();
    CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

    while (b.getAction(InputAction.class) == null) {
      e.waitForSuspension();
    }

    // make sure we are pausing at the right state that reflects what we wrote in the program
    InputAction a = b.getAction(InputAction.class);
    assertEquals(2, a.getExecutions().size());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page;

    InputStepExecution is1 = a.getExecution("Cell");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(1, a.getExecutions().size());

    is1 = a.getExecution("Cell");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("One Continues in A", b);
    j.assertLogContains("Two Continues in A", b);
    j.assertLogContains("Stage \"Cell\" skipped due to when conditional", b);
    j.assertLogNotContains("One Continues in B", b);
    j.assertLogNotContains("Two Continues in B", b);
  }

  @Test
  public void matrixInputWhenBeforeInput() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "matrixInputWhenBeforeInput");
    p.setDefinition(
        new CpsFlowDefinition(
            pipelineSourceFromResources("matrix/matrixInputWhenBeforeInput"), true));
    // get the build going, and wait until workflow pauses
    QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
    WorkflowRun b = q.getStartCondition().get();
    CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

    while (b.getAction(InputAction.class) == null) {
      e.waitForSuspension();
    }

    // make sure we are pausing at the right state that reflects what we wrote in the program
    InputAction a = b.getAction(InputAction.class);
    assertEquals(1, a.getExecutions().size());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page;

    InputStepExecution is1 = a.getExecution("Matrix - AXIS_VALUE = 'A'");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("One Continues in A", b);
    j.assertLogContains("Two Continues in A", b);
    j.assertLogContains("Stage \"Matrix - AXIS_VALUE = 'B'\" skipped due to when conditional", b);
    j.assertLogNotContains("One Continues in B", b);
    j.assertLogNotContains("Two Continues in B", b);
  }

  @Test
  public void matrixInputWhenBeforeInputChildStage() throws Exception {
    WorkflowJob p =
        j.jenkins.createProject(WorkflowJob.class, "matrixInputWhenBeforeInputChildStage");
    p.setDefinition(
        new CpsFlowDefinition(
            pipelineSourceFromResources("matrix/matrixInputWhenBeforeInputChildStage"), true));
    // get the build going, and wait until workflow pauses
    QueueTaskFuture<WorkflowRun> q = p.scheduleBuild2(0);
    WorkflowRun b = q.getStartCondition().get();
    CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

    while (b.getAction(InputAction.class) == null) {
      e.waitForSuspension();
    }

    // make sure we are pausing at the right state that reflects what we wrote in the program
    InputAction a = b.getAction(InputAction.class);
    assertEquals(1, a.getExecutions().size());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page;

    InputStepExecution is1 = a.getExecution("Cell");
    assertEquals("Continue?", is1.getInput().getMessage());
    assertEquals(0, is1.getInput().getParameters().size());
    assertNull(is1.getInput().getSubmitter());

    page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is1.getId()), "proceed");

    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("One Continues in A", b);
    j.assertLogContains("Two Continues in A", b);
    j.assertLogContains("Stage \"Cell\" skipped due to when conditional", b);
    j.assertLogNotContains("One Continues in B", b);
    j.assertLogNotContains("Two Continues in B", b);
  }
}
