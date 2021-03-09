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

package org.jenkinsci.plugins.pipeline.modeldefinition.cli;

import static hudson.cli.CLICommandInvoker.Matcher.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import hudson.cli.CLICommandInvoker;
import hudson.model.Item;
import hudson.model.User;
import java.io.File;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class DeclarativeLinterCommandTest extends AbstractModelDefTest {

  private DeclarativeLinterCommand declarativeLinterCommand;
  private CLICommandInvoker command;

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Before
  public void setUpPerTest() {
    declarativeLinterCommand = new DeclarativeLinterCommand();
    command = new CLICommandInvoker(j, declarativeLinterCommand);
  }

  @Test
  public void validJenkinsfile() throws Exception {
    File testPath = writeJenkinsfileToTmpFile("simplePipeline");
    j.jenkins.disableSecurity();

    final CLICommandInvoker.Result result =
        command.withStdin(FileUtils.openInputStream(testPath)).invoke();

    assertThat(result, succeeded());
    assertThat(result, hasNoErrorOutput());
    assertThat(result.stdout(), containsString("Jenkinsfile successfully validated."));
  }

  @Test
  public void invalidJenkinsfileEmptyAgent() throws Exception {
    File testPath = writeJenkinsfileToTmpFile("errors", "emptyAgent");
    j.jenkins.disableSecurity();

    final CLICommandInvoker.Result result =
        command.withStdin(FileUtils.openInputStream(testPath)).invoke();

    assertThat(result, failedWith(1));
    assertThat(result, hasNoErrorOutput());
    assertThat(result.stdout(), containsString("Errors encountered validating Jenkinsfile:"));
    assertThat(
        result.stdout(),
        containsString(
            "Not a valid section definition: \"agent\". Some extra configuration is required"));
  }

  @Test
  public void invalidJenkinsfileEmptyPipeline() throws Exception {
    File testPath = writeJenkinsfileToTmpFile("errors", "emptyPipeline");
    j.jenkins.disableSecurity();

    final CLICommandInvoker.Result result =
        command.withStdin(FileUtils.openInputStream(testPath)).invoke();

    assertThat(result, failedWith(1));
    assertThat(result, hasNoErrorOutput());
    assertThat(result.stdout(), containsString("did not contain the 'pipeline' step"));
  }

  @Test
  public void invalidUser() throws Exception {
    File testPath = writeJenkinsfileToTmpFile("simplePipeline");

    j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    j.jenkins.setAuthorizationStrategy(
        new MockAuthorizationStrategy()
            .grant(Jenkins.ADMINISTER)
            .everywhere()
            .to("bob")
            .grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ)
            .everywhere()
            .to("alice"));

    final CLICommandInvoker.Result result =
        command.withStdin(FileUtils.openInputStream(testPath)).invoke();

    assertThat(result, not(succeeded()));
    assertThat(
        result.stderr(), containsString("ERROR: anonymous is missing the Overall/Read permission"));

    declarativeLinterCommand.setTransportAuth(User.getById("alice", true).impersonate());
    final CLICommandInvoker.Result result2 =
        command.withStdin(FileUtils.openInputStream(testPath)).invoke();

    assertThat(result2, succeeded());
    assertThat(result2, hasNoErrorOutput());
    assertThat(result2.stdout(), containsString("Jenkinsfile successfully validated."));
  }

  private File writeJenkinsfileToTmpFile(String dir, String testName) throws IOException {
    return writeJenkinsfileToTmpFile(dir + "/" + testName);
  }

  private File writeJenkinsfileToTmpFile(String testName) throws IOException {
    File jf = tmp.newFile();

    String contents = pipelineSourceFromResources(testName);

    FileUtils.writeStringToFile(jf, contents);

    return jf;
  }
}
