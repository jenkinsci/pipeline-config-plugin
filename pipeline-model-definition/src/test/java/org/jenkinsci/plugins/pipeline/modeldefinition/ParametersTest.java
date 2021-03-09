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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import hudson.model.BooleanParameterDefinition;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.StringParameterDefinition;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class ParametersTest extends AbstractModelDefTest {
  @Test
  public void simpleParameters() throws Exception {
    WorkflowRun b =
        expect("simpleParameters")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
            .go();

    WorkflowJob p = b.getParent();

    ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(pdp);

    assertEquals(1, pdp.getParameterDefinitions().size());
    assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
    BooleanParameterDefinition bpd =
        (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
    assertEquals("flag", bpd.getName());
    assertTrue(bpd.isDefaultValue());
  }

  @Test
  public void simpleParametersWithOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    WorkflowRun b =
        expect("simpleParametersWithOutsideVarAndFunc")
            .logContains("[Pipeline] { (foo)", "hello true: Hi there - This comes from a function")
            .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
            .go();

    WorkflowJob p = b.getParent();

    ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(pdp);

    assertEquals(2, pdp.getParameterDefinitions().size());
    assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
    BooleanParameterDefinition bpd =
        (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
    assertEquals("flag", bpd.getName());
    assertTrue(bpd.isDefaultValue());

    assertEquals(StringParameterDefinition.class, pdp.getParameterDefinitions().get(1).getClass());
    StringParameterDefinition spd =
        (StringParameterDefinition) pdp.getParameterDefinitions().get(1);
    assertEquals("JENKINS_LABEL", spd.getName());
    assertEquals("Hi there - This comes from a function", spd.getDefaultValue());
  }

  @Ignore("Parameters are set before withEnv is called.")
  @Test
  public void envVarInParameters() throws Exception {
    WorkflowRun b =
        expect("environment/envVarInParameters")
            .logContains("[Pipeline] { (foo)", "hello")
            .logNotContains("[Pipeline] { (" + SyntheticStageNames.postBuild() + ")")
            .go();

    WorkflowJob p = b.getParent();

    ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(pdp);

    assertEquals(1, pdp.getParameterDefinitions().size());
    assertEquals(BooleanParameterDefinition.class, pdp.getParameterDefinitions().get(0).getClass());
    BooleanParameterDefinition bpd =
        (BooleanParameterDefinition) pdp.getParameterDefinitions().get(0);
    assertEquals("flag", bpd.getName());
    assertTrue(bpd.isDefaultValue());
  }

  @Issue("JENKINS-44149")
  @Test
  public void paramsRemoved() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    ParametersDefinitionProperty paramProp = job.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(paramProp);
    assertEquals(1, paramProp.getParameterDefinitions().size());

    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    assertNull(job.getProperty(ParametersDefinitionProperty.class));
  }

  @Issue("JENKINS-44621")
  @Test
  public void externalParamsNotRemoved() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    ParametersDefinitionProperty paramProp = job.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(paramProp);
    assertEquals(1, paramProp.getParameterDefinitions().size());

    List<ParameterDefinition> newParams = new ArrayList<>();
    newParams.addAll(paramProp.getParameterDefinitions());
    newParams.add(new StringParameterDefinition("DO_NOT_DELETE", "something"));
    job.removeProperty(paramProp);
    job.addProperty(new ParametersDefinitionProperty(newParams));

    job.setDefinition(
        new CpsFlowDefinition(pipelineSourceFromResources("propsTriggersParamsRemoved"), true));
    j.buildAndAssertSuccess(job);

    ParametersDefinitionProperty newProp = job.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(newProp);
    assertEquals(1, newProp.getParameterDefinitions().size());
    ParameterDefinition paramDef = newProp.getParameterDefinition("DO_NOT_DELETE");
    assertNotNull(paramDef);
  }

  @Test
  public void sameParametersNotOverride() throws Exception {
    WorkflowRun b = getAndStartNonRepoBuild("simpleParameters");
    j.assertBuildStatusSuccess(j.waitForCompletion(b));

    WorkflowJob job = b.getParent();
    ParametersDefinitionProperty paramProp = job.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(paramProp);
    BooleanParameterDefinition bpd =
        (BooleanParameterDefinition) paramProp.getParameterDefinitions().get(0);

    WorkflowRun b2 = job.scheduleBuild2(0).get();
    j.assertBuildStatusSuccess(j.waitForCompletion(b2));
    WorkflowJob job2 = b2.getParent();
    ParametersDefinitionProperty paramProp2 = job2.getProperty(ParametersDefinitionProperty.class);
    assertNotNull(paramProp2);
    BooleanParameterDefinition bpd2 =
        (BooleanParameterDefinition) paramProp2.getParameterDefinitions().get(0);
    assertSame(bpd, bpd2);
  }

  @Issue("JENKINS-63499")
  @Test
  public void passwordParameters() throws Exception {
    WorkflowRun b =
        expect("passwordParameters").runFromRepo(false).logContains("Password is mySecret").go();
    WorkflowJob p = b.getParent();

    ParametersDefinitionProperty pdp = p.getProperty(ParametersDefinitionProperty.class);
    assertThat(pdp.getParameterDefinitions(), hasSize(1));
    PasswordParameterDefinition param =
        (PasswordParameterDefinition) pdp.getParameterDefinitions().get(0);
    assertEquals("myPassword", param.getName());
    assertEquals("mySecret", param.getDefaultValue());
    assertEquals("myDescription", param.getDescription());
  }
}
