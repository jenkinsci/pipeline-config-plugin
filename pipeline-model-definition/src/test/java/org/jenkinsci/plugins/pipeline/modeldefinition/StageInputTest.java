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

package org.jenkinsci.plugins.pipeline.modeldefinition;

import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.html.*;
import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class StageInputTest extends AbstractModelDefTest {

  @Issue("JENKINS-48379")
  @Test
  public void simpleInput() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "simpleInput");
    p.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("simpleInput"), true));
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

    InputStepExecution is = a.getExecution("Foo");
    assertEquals("Continue?", is.getInput().getMessage());
    assertEquals(0, is.getInput().getParameters().size());
    assertNull(is.getInput().getSubmitter());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is.getId()), "proceed");
    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("hello", b);
  }

  @Test
  public void simpleInputWithOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "simpleInputWithOutsideVarAndFunc");
    p.setDefinition(
        new CpsFlowDefinition(
            pipelineSourceFromResources("simpleInputWithOutsideVarAndFunc"), true));
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

    InputStepExecution is = a.getExecution("Foo");
    assertEquals("Continue?", is.getInput().getMessage());
    assertEquals(0, is.getInput().getParameters().size());
    assertNull(is.getInput().getSubmitter());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is.getId()), "proceed");
    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("hello", b);
  }

  @Issue("JENKINS-48379")
  @Test
  public void parametersInInput() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "parametersInInput");
    p.setDefinition(new CpsFlowDefinition(pipelineSourceFromResources("parametersInInput"), true));
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

    InputStepExecution is = a.getExecution("Simple-input");
    assertEquals("Continue?", is.getInput().getMessage());
    assertEquals(2, is.getInput().getParameters().size());
    assertNull(is.getInput().getSubmitter());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page = wc.getPage(b, a.getUrlName());
    HtmlForm form = page.getFormByName(is.getId());

    HtmlElement element =
        DomNodeUtil.selectSingleNode(
            form, "//tr[td/div/input/@value='fruit'] | //div[div/div/input/@value='fruit']");
    assertNotNull(element);

    HtmlTextInput stringParameterInput =
        DomNodeUtil.selectSingleNode(element, ".//input[@name='value']");
    assertEquals("banana", stringParameterInput.getAttribute("value"));
    assertEquals(
        "fruit",
        ((HtmlElement)
                DomNodeUtil.selectSingleNode(
                    element, "td[@class='setting-name'] | div[contains(@class,'setting-name')]"))
            .getTextContent());
    stringParameterInput.setAttribute("value", "pear");

    element =
        DomNodeUtil.selectSingleNode(
            form, "//tr[td/div/input/@value='flag'] | //div[div/div/input/@value='flag']");
    assertNotNull(element);

    Object o = DomNodeUtil.selectSingleNode(element, ".//input[@name='value']");

    HtmlCheckBoxInput booleanParameterInput = (HtmlCheckBoxInput) o;
    booleanParameterInput.setChecked(false);

    j.submit(page.getFormByName(is.getId()), "proceed");
    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("Would I like to eat a pear? false", b);
  }

  @Issue("JENKINS-48379")
  @Test
  public void singleParameterInInput() throws Exception {
    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "singleParameterInInput");
    p.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("singleParameterInInput"), true));
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

    InputStepExecution is = a.getExecution("Simple-input");
    assertEquals("Continue?", is.getInput().getMessage());
    assertEquals(1, is.getInput().getParameters().size());
    assertNull(is.getInput().getSubmitter());

    JenkinsRule.WebClient wc = j.createWebClient();
    HtmlPage page = wc.getPage(b, a.getUrlName());
    HtmlForm form = page.getFormByName(is.getId());

    HtmlElement element =
        DomNodeUtil.selectSingleNode(
            form, "//tr[td/div/input/@value='flag'] | //div[div/div/input/@value='flag']");
    assertNotNull(element);

    Object o = DomNodeUtil.selectSingleNode(element, ".//input[@name='value']");

    HtmlCheckBoxInput booleanParameterInput = (HtmlCheckBoxInput) o;
    booleanParameterInput.setChecked(false);

    j.submit(page.getFormByName(is.getId()), "proceed");
    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("Would I like to eat? false", b);
  }

  @Issue("JENKINS-48379")
  @Test
  public void submitterParameterInInput() throws Exception {
    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

    WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "submitterParameterInInput");
    p.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("submitterParameterInInput"), true));
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

    InputStepExecution is = a.getExecution("Simple-input");
    assertEquals("Continue?", is.getInput().getMessage());
    assertEquals(0, is.getInput().getParameters().size());
    assertEquals("alice", is.getInput().getSubmitter());

    JenkinsRule.WebClient wc = j.createWebClient();
    wc.login("alice");
    HtmlPage page = wc.getPage(b, a.getUrlName());
    j.submit(page.getFormByName(is.getId()), "proceed");
    assertEquals(0, a.getExecutions().size());
    q.get();

    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    j.assertLogContains("Who approved? alice", b);
  }
}
