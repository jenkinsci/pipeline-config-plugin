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

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Result;
import hudson.model.Slave;
import java.util.Arrays;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.RuntimeASTTransformer;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/** @author Andrew Bayer */
public class LibrariesTest extends AbstractModelDefTest {

  private static Slave s;

  @BeforeClass
  public static void setUpAgent() throws Exception {
    s = j.createOnlineSlave();
    s.setNumExecutors(10);
    s.setLabelString("some-label");
  }

  @Test
  public void globalLibrarySuccess() throws Exception {

    initGlobalLibrary();

    // Test the successful, albeit limited, case.
    expect("libraries/globalLibrarySuccess")
        .logContains(
            "[nothing here]",
            "map call(1,2)",
            "closure1(1)",
            "running inside closure1",
            "closure2(1, 2)",
            "running inside closure2")
        .go();
  }

  @Issue("JENKINS-45081")
  @Test
  public void objectMethodPipelineCall() throws Exception {
    initGlobalLibrary();

    expect("libraries/objectMethodPipelineCall").logContains("Hi there").go();
  }

  @Issue("JENKINS-40642")
  @Test
  public void libraryAnnotation() throws Exception {
    otherRepo.init();
    otherRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
    otherRepo.write("vars/myecho.txt", "Says something very special!");
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    GlobalLibraries.get()
        .setLibraries(
            Collections.singletonList(
                new LibraryConfiguration(
                    "echo-utils",
                    new SCMSourceRetriever(
                        new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

    expect("libraries/libraryAnnotation").logContains("something special").go();
  }

  @Issue("JENKINS-38110")
  @Test
  public void librariesDirective() throws Exception {
    otherRepo.init();
    otherRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
    otherRepo.write("vars/myecho.txt", "Says something very special!");
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    LibraryConfiguration firstLib =
        new LibraryConfiguration(
            "echo-utils",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

    thirdRepo.init();
    thirdRepo.write("vars/whereFrom.groovy", "def call() {echo 'from another library'}");
    thirdRepo.write("vars/whereFrom.txt", "Says where it's from!");
    thirdRepo.git("add", "vars");
    thirdRepo.git("commit", "--message=init");
    LibraryConfiguration secondLib =
        new LibraryConfiguration(
            "whereFrom",
            new SCMSourceRetriever(
                new GitSCMSource(null, thirdRepo.toString(), "", "*", "", true)));
    secondLib.setDefaultVersion("master");
    GlobalLibraries.get().setLibraries(Arrays.asList(firstLib, secondLib));

    expect("libraries/librariesDirective")
        .logContains("something special", "from another library")
        .go();
  }

  @Issue("JENKINS-38110")
  @Test
  public void librariesDirectiveWithOutsideVarAndFunc() throws Exception {
    // this should have same behavior whether script splitting is enable or not
    RuntimeASTTransformer.SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = true;

    otherRepo.init();
    otherRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
    otherRepo.write("vars/myecho.txt", "Says something very special!");
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    LibraryConfiguration firstLib =
        new LibraryConfiguration(
            "echo-utils",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

    thirdRepo.init();
    thirdRepo.write("vars/whereFrom.groovy", "def call() {echo 'from another library'}");
    thirdRepo.write("vars/whereFrom.txt", "Says where it's from!");
    thirdRepo.git("add", "vars");
    thirdRepo.git("commit", "--message=init");
    LibraryConfiguration secondLib =
        new LibraryConfiguration(
            "whereFrom",
            new SCMSourceRetriever(
                new GitSCMSource(null, thirdRepo.toString(), "", "*", "", true)));
    secondLib.setDefaultVersion("master");
    GlobalLibraries.get().setLibraries(Arrays.asList(firstLib, secondLib));

    expect("libraries/librariesDirectiveWithOutsideVarAndFunc")
        .logContains("something special", "from another library")
        .go();
  }

  @Issue("JENKINS-42473")
  @Test
  public void folderLibraryParsing() throws Exception {
    otherRepo.init();
    otherRepo.git("checkout", "-b", "test");
    otherRepo.write(
        "src/org/foo/Zot.groovy",
        "package org.foo;\n" + "\n" + "def echo(msg) {\n" + "  echo \"-> ${msg}\"\n" + "}\n");
    otherRepo.git("add", "src");
    otherRepo.git("commit", "--message=init");
    Folder folder = j.jenkins.createProject(Folder.class, "testFolder");
    LibraryConfiguration echoLib =
        new LibraryConfiguration(
            "zot-stuff",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));
    folder.getProperties().add(new FolderLibraries(Collections.singletonList(echoLib)));

    WorkflowRun firstRun =
        expect("libraries/folderLibraryParsing").inFolder(folder).logContains("Hello world").go();

    WorkflowRun secondRun = j.buildAndAssertSuccess(firstRun.getParent());
    ExecutionModelAction action = secondRun.getAction(ExecutionModelAction.class);
    assertNotNull(action);
    ModelASTStages stages = action.getStages();
    assertNull(stages.getSourceLocation());
    assertNotNull(stages);
  }

  @Issue("JENKINS-40657")
  @Test
  public void libraryObjectInScript() throws Exception {
    otherRepo.init();
    otherRepo.write(
        "src/org/foo/Zot.groovy",
        "package org.foo;\n"
            + "\n"
            + "class Zot implements Serializable {\n"
            + "  def steps\n"
            + "  Zot(steps){\n"
            + "    this.steps = steps\n"
            + "  }\n"
            + "  def echo(msg) {\n"
            + "    steps.echo \"${msg}\"\n"
            + "  }\n"
            + "}\n");
    otherRepo.git("add", "src");
    otherRepo.git("commit", "--message=init");
    GlobalLibraries.get()
        .setLibraries(
            Collections.singletonList(
                new LibraryConfiguration(
                    "zot-stuff",
                    new SCMSourceRetriever(
                        new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

    expect("libraries/libraryObjectInScript").logContains("hello").go();
  }

  @Issue("JENKINS-40657")
  @Test
  public void libraryObjectDefinedOutsidePipeline() throws Exception {
    otherRepo.init();
    otherRepo.write(
        "src/org/foo/Zot.groovy",
        "package org.foo;\n"
            + "\n"
            + "class Zot implements Serializable {\n"
            + "  def steps\n"
            + "  Zot(steps){\n"
            + "    this.steps = steps\n"
            + "  }\n"
            + "  def echo(msg) {\n"
            + "    steps.echo \"${msg}\"\n"
            + "  }\n"
            + "}\n");
    otherRepo.git("add", "src");
    otherRepo.git("commit", "--message=init");
    GlobalLibraries.get()
        .setLibraries(
            Collections.singletonList(
                new LibraryConfiguration(
                    "zot-stuff",
                    new SCMSourceRetriever(
                        new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

    expect("libraries/libraryObjectDefinedOutsidePipeline").logContains("hello");
  }

  @Issue("JENKINS-46547")
  @Test
  public void pipelineDefinedInLibrary() throws Exception {
    otherRepo.init();
    otherRepo.write(
        "vars/fromLib.groovy",
        pipelineSourceFromResources("libraries/libForPipelineDefinedInLibrary"));
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    LibraryConfiguration firstLib =
        new LibraryConfiguration(
            "from-lib",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

    GlobalLibraries.get().setLibraries(Arrays.asList(firstLib));

    expect("libraries/pipelineDefinedInLibrary")
        .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
        .logNotContains("World")
        .go();
  }

  @Issue("JENKINS-46547")
  @Test
  public void pipelineDefinedInLibraryInFolder() throws Exception {
    otherRepo.init();
    otherRepo.write(
        "vars/fromLib.groovy",
        pipelineSourceFromResources("libraries/libForPipelineDefinedInLibrary"));
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    LibraryConfiguration firstLib =
        new LibraryConfiguration(
            "from-lib",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));
    Folder folder = j.jenkins.createProject(Folder.class, "libInFolder");
    folder.getProperties().add(new FolderLibraries(Collections.singletonList(firstLib)));

    expect("libraries/pipelineDefinedInLibrary")
        .inFolder(folder)
        .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
        .logNotContains("World")
        .go();
  }

  @Issue("JENKINS-46547")
  @Test
  public void multiplePipelinesDefinedInLibrary() throws Exception {
    otherRepo.init();
    otherRepo.write(
        "vars/fromLib.groovy",
        pipelineSourceFromResources("libraries/libForMultiplePipelinesDefinedInLibrary"));
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    LibraryConfiguration firstLib =
        new LibraryConfiguration(
            "from-lib",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

    GlobalLibraries.get().setLibraries(Arrays.asList(firstLib));

    WorkflowRun firstRun =
        expect("libraries/multiplePipelinesDefinedInLibraryFirst")
            .runFromRepo(false)
            .logContains("[Pipeline] { (One)", "[Pipeline] { (Two)")
            .logNotContains("World")
            .go();

    ExecutionModelAction firstAction = firstRun.getAction(ExecutionModelAction.class);
    assertNotNull(firstAction);
    ModelASTStages firstStages = firstAction.getStages();
    assertNotNull(firstStages);
    assertEquals(2, firstStages.getStages().size());

    WorkflowRun secondRun =
        expect("libraries/multiplePipelinesDefinedInLibrarySecond")
            .runFromRepo(false)
            .logContains("[Pipeline] { (Different)", "This is the alternative pipeline")
            .go();

    ExecutionModelAction secondAction = secondRun.getAction(ExecutionModelAction.class);
    assertNotNull(secondAction);
    ModelASTStages secondStages = secondAction.getStages();
    assertNotNull(secondStages);
    assertEquals(1, secondStages.getStages().size());
  }

  @Issue("JENKINS-46547")
  @Test
  public void multiplePipelinesExecutedInLibraryShouldFail() throws Exception {
    otherRepo.init();
    otherRepo.write(
        "vars/fromLib.groovy",
        pipelineSourceFromResources("libraries/libForMultiplePipelinesExecutedInLibrary"));
    otherRepo.git("add", "vars");
    otherRepo.git("commit", "--message=init");
    LibraryConfiguration firstLib =
        new LibraryConfiguration(
            "from-lib",
            new SCMSourceRetriever(
                new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)));

    GlobalLibraries.get().setLibraries(Arrays.asList(firstLib));

    expect(Result.FAILURE, "libraries/pipelineDefinedInLibrary")
        .logContains(
            "java.lang.IllegalStateException: Only one pipeline { ... } block can be executed in a single run")
        .go();
  }

  @Issue("JENKINS-43035")
  @Test
  public void libraryObjectImportInWhenExpr() throws Exception {
    otherRepo.init();
    otherRepo.write(
        "src/org/foo/Zot.groovy",
        "package org.foo;\n"
            + "\n"
            + "class Zot implements Serializable {\n"
            + "  def steps\n"
            + "  Zot(steps){\n"
            + "    this.steps = steps\n"
            + "  }\n"
            + "  def echo(msg) {\n"
            + "    steps.echo \"${msg}\"\n"
            + "  }\n"
            + "}\n");
    otherRepo.git("add", "src");
    otherRepo.git("commit", "--message=init");
    GlobalLibraries.get()
        .setLibraries(
            Collections.singletonList(
                new LibraryConfiguration(
                    "zot-stuff",
                    new SCMSourceRetriever(
                        new GitSCMSource(null, otherRepo.toString(), "", "*", "", true)))));

    expect("libraries/libraryObjectImportInWhenExpr").logContains("hello").go();
  }
}
