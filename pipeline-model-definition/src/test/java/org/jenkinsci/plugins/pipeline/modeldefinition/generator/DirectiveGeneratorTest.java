/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BooleanParameterDefinition;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.StringParameterDefinition;
import hudson.tasks.LogRotator;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jenkins.model.BuildDiscarderProperty;
import jenkins.model.OptionalJobProperty;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl.*;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.impl.SkipDefaultCheckout;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.impl.*;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.workflow.steps.TimeoutStep;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.ToolInstallations;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DirectiveGeneratorTest {
  public static class TestDescribable extends AbstractDescribableImpl<TestDescribable> {
    private String name;

    @DataBoundConstructor
    public TestDescribable() {}

    public String getName() {
      return name;
    }

    @DataBoundSetter
    public void setName(String name) {
      this.name = name;
    }

    @TestExtension
    @Symbol("test")
    public static class DescriptorImpl extends Descriptor<TestDescribable> {}
  }

  public static class ComplexDeclarativeAgent extends DeclarativeAgent<ComplexDeclarativeAgent> {
    private TestDescribable someField;

    @DataBoundConstructor
    public ComplexDeclarativeAgent() {}

    public TestDescribable getSomeField() {
      return someField;
    }

    @DataBoundSetter
    public void setSomeField(TestDescribable someField) {
      this.someField = someField;
    }

    @TestExtension
    @Symbol("complex")
    public static class DescriptorImpl
        extends DeclarativeAgentDescriptor<ComplexDeclarativeAgent> {}
  }

  @ClassRule public static JenkinsRule r = new JenkinsRule();

  public static DirectiveGeneratorTester dg;

  @BeforeClass
  public static void setUp() throws Exception {
    ToolInstallations.configureMaven3();
    dg = new DirectiveGeneratorTester(r);
  }

  @Issue("JENKINS-51027")
  @Test
  public void buildConditionsHaveDescriptions() throws Exception {
    PostDirective.DescriptorImpl descriptor =
        r.jenkins.getDescriptorByType(PostDirective.DescriptorImpl.class);
    assertNotNull(descriptor);
    List<BuildCondition> conditions = ExtensionList.lookup(BuildCondition.class);
    assertEquals(conditions.size(), descriptor.getPossibleConditions().size());
  }

  @Test
  public void simpleInput() throws Exception {
    InputDirective input = new InputDirective("hello");
    dg.assertGenerateDirective(input, "input {\n" + "  message 'hello'\n" + "}");
  }

  // TODO: Remove once we move to a baseline of 2.90 or later
  private String trimParamOrEmpty() {
    try {
      StringParameterDefinition.class.getMethod("isTrim");
      return ", trim: false";
    } catch (NoSuchMethodException e) {
      return "";
    }
  }

  @Test
  public void fullInput() throws Exception {
    InputDirective input = new InputDirective("hello");
    input.setId("banana");
    input.setOk("Yeah, do it");
    input.setSubmitter("bob");
    input.setSubmitterParameter("subParam");
    List<ParameterDefinition> params = new ArrayList<>();
    params.add(new StringParameterDefinition("aString", "steve", "Hey, a string"));
    params.add(new BooleanParameterDefinition("aBool", true, "A boolean now"));
    input.setParameters(params);
    dg.assertGenerateDirective(
        input,
        "input {\n"
            + "  message 'hello'\n"
            + "  id 'banana'\n"
            + "  ok 'Yeah, do it'\n"
            + "  submitter 'bob'\n"
            + "  submitterParameter 'subParam'\n"
            + "  parameters {\n"
            +
            // StringParameterDefinition added trim field in 2.90
            "    string defaultValue: 'steve', description: 'Hey, a string', name: 'aString'"
            + trimParamOrEmpty()
            + "\n"
            + "    booleanParam defaultValue: true, description: 'A boolean now', name: 'aBool'\n"
            + "  }\n"
            + "}");
  }

  @Test
  public void agentAny() throws Exception {
    AgentDirective agent = new AgentDirective(new Any());
    dg.assertGenerateDirective(agent, "agent any");
  }

  @Test
  public void agentNone() throws Exception {
    AgentDirective agent = new AgentDirective(new None());
    dg.assertGenerateDirective(agent, "agent none");
  }

  @Test
  public void agentLabel() throws Exception {
    AgentDirective agent = new AgentDirective(new Label("some-label"));
    dg.assertGenerateDirective(agent, "agent {\n" + "  label 'some-label'\n" + "}");
  }

  @Test
  public void whenBranch() throws Exception {
    WhenDirective when =
        new WhenDirective(new BranchConditional("some-pattern"), true, false, false);

    dg.assertGenerateDirective(
        when, "when {\n" + "  branch 'some-pattern'\n" + "  beforeAgent true\n" + "}");
  }

  @Test
  public void agentComplex() throws Exception {
    ComplexDeclarativeAgent complex = new ComplexDeclarativeAgent();
    TestDescribable someField = new TestDescribable();
    someField.setName("myName");
    complex.setSomeField(someField);
    AgentDirective agent = new AgentDirective(complex);
    dg.assertGenerateDirective(
        agent,
        "agent {\n" + "  complex {\n" + "    someField test(name: 'myName')\n" + "  }\n" + "}");
  }

  @Test
  public void whenBranchBeforeInput() throws Exception {
    WhenDirective when =
        new WhenDirective(new BranchConditional("some-pattern"), false, true, false);

    dg.assertGenerateDirective(
        when, "when {\n" + "  branch 'some-pattern'\n" + "  beforeInput true\n" + "}");
  }

  @Test
  public void whenBranchBeforeOptions() throws Exception {
    WhenDirective when =
        new WhenDirective(new BranchConditional("some-pattern"), false, false, true);

    dg.assertGenerateDirective(
        when, "when {\n" + "  branch 'some-pattern'\n" + "  beforeOptions true\n" + "}");
  }

  @Test
  public void whenEnvironment() throws Exception {
    WhenDirective when =
        new WhenDirective(
            new EnvironmentConditional("SOME_VAR", "some value"), false, false, false);

    dg.assertGenerateDirective(
        when, "when {\n" + "  environment name: 'SOME_VAR', value: 'some value'\n" + "}");
  }

  @Test
  public void whenChangelog() throws Exception {
    WhenDirective when =
        new WhenDirective(new ChangeLogConditional("some-pattern"), false, false, false);
    dg.assertGenerateDirective(when, "when {\n" + "  changelog 'some-pattern'\n" + "}");
  }

  @Test
  public void whenChangeset() throws Exception {
    WhenDirective when =
        new WhenDirective(new ChangeSetConditional("some/file/in/changeset"), false, false, false);

    dg.assertGenerateDirective(when, "when {\n" + "  changeset 'some/file/in/changeset'\n" + "}");
  }

  @Test
  public void whenNot() throws Exception {
    WhenDirective when =
        new WhenDirective(
            new NotConditional(new BranchConditional("some-bad-branch")), false, false, false);

    dg.assertGenerateDirective(
        when, "when {\n" + "  not {\n" + "    branch 'some-bad-branch'\n" + "  }\n" + "}");
  }

  @Test
  public void whenAnyOf() throws Exception {
    List<DeclarativeStageConditional<?>> nested = new ArrayList<>();
    nested.add(new BranchConditional("that-branch"));
    nested.add(new BranchConditional("this-branch"));
    WhenDirective when = new WhenDirective(new AnyOfConditional(nested), true, false, false);

    dg.assertGenerateDirective(
        when,
        "when {\n"
            + "  anyOf {\n"
            + "    branch 'that-branch'\n"
            + "    branch 'this-branch'\n"
            + "  }\n"
            + "  beforeAgent true\n"
            + "}");
  }

  @Test
  public void whenAllOf() throws Exception {
    List<DeclarativeStageConditional<?>> nested = new ArrayList<>();
    nested.add(new BranchConditional("that-branch"));
    nested.add(new BranchConditional("this-branch"));
    WhenDirective when = new WhenDirective(new AllOfConditional(nested), false, false, false);

    dg.assertGenerateDirective(
        when,
        "when {\n"
            + "  allOf {\n"
            + "    branch 'that-branch'\n"
            + "    branch 'this-branch'\n"
            + "  }\n"
            + "}");
  }

  @Test
  public void whenAllOfEmpty() throws Exception {
    WhenDirective when = new WhenDirective(new AllOfConditional(null), false, false, false);

    dg.assertGenerateDirective(when, "when {\n" + "  allOf {\n" + "  }\n" + "}");
  }

  @Test
  public void whenDeepNested() throws Exception {
    List<DeclarativeStageConditional<?>> nested = new ArrayList<>();
    nested.add(new BranchConditional("that-branch"));
    nested.add(new BranchConditional("this-branch"));
    nested.add(new NotConditional(new EnvironmentConditional("BOB", "steve")));

    List<DeclarativeStageConditional<?>> veryNested = new ArrayList<>();
    veryNested.add(new ChangeLogConditional("some-pattern"));
    veryNested.add(new NotConditional(new ChangeSetConditional("some/file/do/not/care")));

    nested.add(new AnyOfConditional(veryNested));

    WhenDirective whenDirective =
        new WhenDirective(new AllOfConditional(nested), false, false, false);

    dg.assertGenerateDirective(
        whenDirective,
        "when {\n"
            + "  allOf {\n"
            + "    branch 'that-branch'\n"
            + "    branch 'this-branch'\n"
            + "    not {\n"
            + "      environment name: 'BOB', value: 'steve'\n"
            + "    }\n"
            + "    anyOf {\n"
            + "      changelog 'some-pattern'\n"
            + "      not {\n"
            + "        changeset 'some/file/do/not/care'\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}");
  }

  @Test
  public void triggersSingle() throws Exception {
    List<Trigger> t = new ArrayList<>();
    t.add(new TimerTrigger("@daily"));

    TriggersDirective triggers = new TriggersDirective(t);

    dg.assertGenerateDirective(triggers, "triggers {\n" + "  cron '@daily'\n" + "}");
  }

  @Test
  public void triggersMultiple() throws Exception {
    List<Trigger> t = new ArrayList<>();
    t.add(new TimerTrigger("@daily"));
    t.add(new SCMTrigger("@hourly"));

    TriggersDirective triggers = new TriggersDirective(t);

    dg.assertGenerateDirective(
        triggers, "triggers {\n" + "  cron '@daily'\n" + "  pollSCM '@hourly'\n" + "}");
  }

  @Test
  public void parametersSingle() throws Exception {
    List<ParameterDefinition> p = new ArrayList<>();
    p.add(
        new StringParameterDefinition(
            "SOME_STRING", "some default", "Hey, a description with a ' in it."));

    ParametersDirective params = new ParametersDirective(p);

    dg.assertGenerateDirective(
        params,
        "parameters {\n"
            + "  string defaultValue: 'some default', description: 'Hey, a description with a \\' in it.', name: 'SOME_STRING'"
            + trimParamOrEmpty()
            + "\n"
            + "}");
  }

  @Test
  public void parametersMultiple() throws Exception {
    List<ParameterDefinition> p = new ArrayList<>();
    p.add(
        new StringParameterDefinition(
            "SOME_STRING", "some default", "Hey, a description with a ' in it."));
    p.add(new BooleanParameterDefinition("SOME_BOOL", true, "This will default to true."));

    ParametersDirective params = new ParametersDirective(p);

    dg.assertGenerateDirective(
        params,
        "parameters {\n"
            + "  string defaultValue: 'some default', description: 'Hey, a description with a \\' in it.', name: 'SOME_STRING'"
            + trimParamOrEmpty()
            + "\n"
            + "  booleanParam defaultValue: true, description: 'This will default to true.', name: 'SOME_BOOL'\n"
            + "}");
  }

  @Test
  public void options() throws Exception {
    List<Describable> o = new ArrayList<>();
    o.add(new BuildDiscarderProperty(new LogRotator("4", "", "", "")));
    o.add(new SkipDefaultCheckout(true));
    TimeoutStep timeout = new TimeoutStep(10);
    timeout.setUnit(TimeUnit.HOURS);
    o.add(timeout);

    OptionsDirective options = new OptionsDirective(o);

    dg.assertGenerateDirective(
        options,
        "options {\n"
            + "  buildDiscarder logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '4', numToKeepStr: '')\n"
            + "  skipDefaultCheckout true\n"
            + "  timeout(time: 10, unit: 'HOURS')\n"
            + "}");
  }

  @Test
  public void tools() throws Exception {
    ToolsDirective tools =
        new ToolsDirective(
            Collections.singletonList(
                new ToolsDirective.SymbolAndName("maven::::apache-maven-3.0.1")));

    dg.assertGenerateDirective(tools, "tools {\n" + "  maven 'apache-maven-3.0.1'\n" + "}");
  }

  @Test
  public void libraries() throws Exception {
    List<LibrariesDirective.NameAndVersion> libList = new ArrayList<>();
    libList.add(new LibrariesDirective.NameAndVersion("first-lib"));
    LibrariesDirective.NameAndVersion second = new LibrariesDirective.NameAndVersion("second-lib");
    second.setVersion("master");
    libList.add(second);
    LibrariesDirective libs = new LibrariesDirective(libList);

    dg.assertGenerateDirective(
        libs, "libraries {\n" + "  lib('first-lib')\n" + "  lib('second-lib@master')\n" + "}");
  }

  @Test
  public void environment() throws Exception {
    List<EnvironmentDirective.NameAndValue> envList = new ArrayList<>();
    envList.add(new EnvironmentDirective.NameAndValue("BOB", "steve"));
    envList.add(new EnvironmentDirective.NameAndValue("WHAT", "${BOB} says hi"));
    EnvironmentDirective env = new EnvironmentDirective(envList);

    dg.assertGenerateDirective(
        env, "environment {\n" + "  BOB = \"steve\"\n" + "  WHAT = \"${BOB} says hi\"\n" + "}");
  }

  @Test
  public void post() throws Exception {
    PostDirective post = new PostDirective(Arrays.asList("always", "unstable"));

    dg.assertGenerateDirective(
        post,
        "post {\n"
            + "  always {\n"
            + "    // One or more steps need to be included within each condition's block.\n"
            + "  }\n"
            + "  unstable {\n"
            + "    // One or more steps need to be included within each condition's block.\n"
            + "  }\n"
            + "}");
  }

  @Test
  public void simpleStage() throws Exception {
    StageDirective stage =
        new StageDirective(Collections.emptyList(), "bob", StageDirective.StageContentType.STEPS);

    dg.assertGenerateDirective(
        stage,
        "stage('bob') {\n"
            + "  steps {\n"
            + "    // One or more steps need to be included within the steps block.\n"
            + "  }\n"
            + "}");
  }

  @Test
  public void simpleParallelStage() throws Exception {
    StageDirective stage =
        new StageDirective(
            Collections.emptyList(), "bob", StageDirective.StageContentType.PARALLEL);

    dg.assertGenerateDirective(
        stage,
        "stage('bob') {\n"
            + "  parallel {\n"
            + "    // One or more stages need to be included within the parallel block.\n"
            + "  }\n"
            + "}");
  }

  @Issue("JENKINS-46809")
  @Test
  public void simpleSequentialStage() throws Exception {
    StageDirective stage =
        new StageDirective(Collections.emptyList(), "bob", StageDirective.StageContentType.STAGES);

    dg.assertGenerateDirective(
        stage,
        "stage('bob') {\n"
            + "  stages {\n"
            + "    // One or more stages need to be included within the stages block.\n"
            + "  }\n"
            + "}");
  }

  @Test
  public void stageWithDirectives() throws Exception {
    ToolsDirective tools =
        new ToolsDirective(
            Collections.singletonList(
                new ToolsDirective.SymbolAndName("maven::::apache-maven-3.0.1")));
    List<EnvironmentDirective.NameAndValue> envList = new ArrayList<>();
    envList.add(new EnvironmentDirective.NameAndValue("BOB", "steve"));
    envList.add(new EnvironmentDirective.NameAndValue("WHAT", "${BOB} says hi"));
    EnvironmentDirective env = new EnvironmentDirective(envList);
    PostDirective post = new PostDirective(Arrays.asList("always", "unstable"));
    List<DeclarativeStageConditional<?>> nested = new ArrayList<>();
    nested.add(new BranchConditional("that-branch"));
    nested.add(new BranchConditional("this-branch"));
    WhenDirective when = new WhenDirective(new AllOfConditional(nested), false, false, false);
    AgentDirective agent = new AgentDirective(new Label("win"));

    StageDirective stage =
        new StageDirective(
            Arrays.asList(agent, when, env, tools, post),
            "bob",
            StageDirective.StageContentType.STEPS);

    dg.assertGenerateDirective(
        stage,
        "stage('bob') {\n"
            + "  steps {\n"
            + "    // One or more steps need to be included within the steps block.\n"
            + "  }\n\n"
            + "  agent {\n"
            + "    label 'win'\n"
            + "  }\n\n"
            + "  when {\n"
            + "    allOf {\n"
            + "      branch 'that-branch'\n"
            + "      branch 'this-branch'\n"
            + "    }\n"
            + "  }\n\n"
            + "  environment {\n"
            + "    BOB = \"steve\"\n"
            + "    WHAT = \"${BOB} says hi\"\n"
            + "  }\n\n"
            + "  tools {\n"
            + "    maven 'apache-maven-3.0.1'\n"
            + "  }\n\n"
            + "  post {\n"
            + "    always {\n"
            + "      // One or more steps need to be included within each condition's block.\n"
            + "    }\n"
            + "    unstable {\n"
            + "      // One or more steps need to be included within each condition's block.\n"
            + "    }\n"
            + "  }\n"
            + "}");
  }

  @Issue("JENKINS-51932")
  @Test
  public void whenIsRestartedRun() throws Exception {
    WhenDirective when = new WhenDirective(new IsRestartedRunConditional(), false, false, false);
    dg.assertGenerateDirective(when, "when {\n" + "  isRestartedRun()\n" + "}");
  }

  @Test
  public void basicMatrix() throws Exception {
    MatrixDirective directive =
        new MatrixDirective(
            Arrays.asList(
                new AxesDirective( // axes
                    Arrays.asList(
                        new AxisDirective("os", "linux, macos, win", false),
                        new AxisDirective("browser", "safari, chrome, ie", false))),
                new ExcludesDirective(
                    Arrays.asList(
                        new ExcludeDirective(
                            Arrays.asList(
                                new AxisDirective("os", "linux", false),
                                new AxisDirective("browser", "safari", false))),
                        new ExcludeDirective(
                            Arrays.asList(
                                new AxisDirective("os", "win", false),
                                new AxisDirective("browser", "ie", true))))),
                new StagesDirective(
                    Arrays.asList(
                        new StageDirective(
                            Arrays.asList(),
                            "build-and-test",
                            StageDirective.StageContentType.STEPS)))));
    dg.assertGenerateDirective(
        directive,
        "matrix {\n"
            + "  axes {\n"
            + "    axis {\n"
            + "      name 'os'\n"
            + "      values 'linux','macos','win'\n"
            + "    }axis {\n"
            + "      name 'browser'\n"
            + "      values 'safari','chrome','ie'\n"
            + "    }\n"
            + "  }\n"
            + "  excludes {\n"
            + "    exclude {\n"
            + "      axis {\n"
            + "        name 'os'\n"
            + "        values 'linux'\n"
            + "      }\n"
            + "      axis {\n"
            + "        name 'browser'\n"
            + "        values 'safari'\n"
            + "      }\n"
            + "    }\n"
            + "    exclude {\n"
            + "      axis {\n"
            + "        name 'os'\n"
            + "        values 'win'\n"
            + "      }\n"
            + "      axis {\n"
            + "        name 'browser'\n"
            + "        notValues 'ie'\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "  stages {\n"
            + "    stage('build-and-test') {\n"
            + "      steps {\n"
            + "        // One or more steps need to be included within the steps block.\n"
            + "      }\n"
            + "    }\n"
            + "\n"
            + "  }\n"
            + "\n"
            + "}");
  }

  /**
   * Tests a form submitting part of the generator.
   *
   * @param desc The describable we'll translate to JSON.
   * @param responseText Expected directive snippet to be generated
   */
  private void assertGenerateDirective(
      @NonNull AbstractDirective desc, @NonNull String responseText) throws Exception {
    // First, make sure the expected response text actually matches the toGroovy for the directive.
    assertEquals(desc.toGroovy(true), responseText);

    // Then submit the form with the appropriate JSON (we generate it from the directive, but it
    // matches the form JSON exactly)
    JenkinsRule.WebClient wc = r.createWebClient();
    WebRequest wrs =
        new WebRequest(new URL(r.getURL(), DirectiveGenerator.GENERATE_URL), HttpMethod.POST);
    List<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new NameValuePair("json", staplerJsonForDescr(desc).toString()));
    // WebClient.addCrumb *replaces* rather than *adds*:
    params.add(
        new NameValuePair(
            r.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(),
            r.jenkins.getCrumbIssuer().getCrumb(null)));
    wrs.setRequestParameters(params);
    WebResponse response = wc.getPage(wrs).getWebResponse();
    assertEquals("text/plain", response.getContentType());
    assertEquals(responseText, response.getContentAsString().trim());
  }

  private Object getValue(DescribableParameter p, Object o) {
    Class<?> ownerClass = o.getClass();
    try {
      try {
        return ownerClass.getField(p.getName()).get(o);
      } catch (NoSuchFieldException x) {
        // OK, check for getter instead
      }
      try {
        return ownerClass.getMethod("get" + p.getCapitalizedName()).invoke(o);
      } catch (NoSuchMethodException x) {
        // one more check
      }
      try {
        return ownerClass.getMethod("is" + p.getCapitalizedName()).invoke(o);
      } catch (NoSuchMethodException x) {
        throw new UnsupportedOperationException(
            "no public field ‘" + p.getName() + "’ (or getter method) found in " + ownerClass);
      }
    } catch (UnsupportedOperationException x) {
      throw x;
    } catch (Exception x) {
      throw new UnsupportedOperationException(x);
    }
  }

  /** TODO: Should probably move this into structs, since it's pretty dang handy. */
  private JSONObject staplerJsonForDescr(Describable d) {
    DescribableModel<?> m = DescribableModel.of(d.getClass());

    JSONObject o = new JSONObject();
    o.accumulate("stapler-class", d.getClass().getName());
    o.accumulate("$class", d.getClass().getName());
    if (d instanceof OptionalJobProperty) {
      o.accumulate("specified", true);
    }
    for (DescribableParameter param : m.getParameters()) {
      Object v = getValue(param, d);
      if (v != null) {
        if (v instanceof Describable) {
          o.accumulate(param.getName(), staplerJsonForDescr((Describable) v));
        } else if (v instanceof List && !((List) v).isEmpty()) {
          JSONArray a = new JSONArray();
          for (Object obj : (List) v) {
            if (obj instanceof Describable) {
              a.add(staplerJsonForDescr((Describable) obj));
            } else if (obj instanceof Number) {
              a.add(obj.toString());
            } else {
              a.add(obj);
            }
          }
          o.accumulate(param.getName(), a);
        } else if (v instanceof Number) {
          o.accumulate(param.getName(), v.toString());
        } else {
          o.accumulate(param.getName(), v);
        }
      }
    }
    return o;
  }
}
