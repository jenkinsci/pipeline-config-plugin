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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import hudson.Launcher;
import hudson.model.ParameterDefinition;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.plugins.git.GitSampleRepoRule;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;
import org.junit.Assume;
import org.junit.Rule;

/** @author Andrew Bayer */
public abstract class AbstractDeclarativeTest {
  @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

  public enum PossibleOS {
    WINDOWS,
    LINUX,
    MAC
  }

  protected void onAllowedOS(PossibleOS... osList) throws Exception {
    boolean passed = false;
    for (PossibleOS os : osList) {
      switch (os) {
        case LINUX:
          if (SystemUtils.IS_OS_LINUX) {
            passed = true;
          }
          break;
        case WINDOWS:
          if (SystemUtils.IS_OS_WINDOWS) {
            passed = true;
          }
          break;
        case MAC:
          if (SystemUtils.IS_OS_MAC) {
            passed = true;
          }
          break;
        default:
          break;
      }
    }

    Assume.assumeTrue("Not on a valid OS for this test", passed);
  }

  protected String pipelineSourceFromResources(String pipelineName) throws IOException {
    return fileContentsFromResources(pipelineName + ".groovy");
  }

  protected String fileContentsFromResources(String fileName) throws IOException {
    return fileContentsFromResources(fileName, false);
  }

  protected String fileContentsFromResources(String fileName, boolean swallowError)
      throws IOException {
    String fileContents = null;

    URL url = getClass().getResource("/" + fileName);
    if (url != null) {
      fileContents = IOUtils.toString(url);
    }

    if (!swallowError) {
      assertNotNull("No file contents for file " + fileName, fileContents);
    } else {
      assumeTrue(fileContents != null);
    }
    return fileContents;
  }

  protected boolean foundExpectedErrorInJSON(JSONArray errors, String expectedError) {
    for (Object e : errors) {
      if (e instanceof JSONObject) {
        JSONObject o = (JSONObject) e;
        if (o.getString("error").equals(expectedError)) {
          return true;
        } else if (o.getString("error").contains(expectedError)) {
          return true;
        }
      }
    }

    return false;
  }

  protected void prepRepoWithJenkinsfile(String pipelineName) throws Exception {
    prepRepoWithJenkinsfileAndOtherFiles(pipelineName);
  }

  protected void prepRepoWithJenkinsfile(String subDir, String pipelineName) throws Exception {
    prepRepoWithJenkinsfileAndOtherFiles(subDir + "/" + pipelineName);
  }

  protected void prepRepoWithJenkinsfileAndOtherFiles(String pipelineName, String... otherFiles)
      throws Exception {
    Map<String, String> otherMap = new HashMap<>();
    for (String otherFile : otherFiles) {
      otherMap.put(otherFile, otherFile);
    }
    prepRepoWithJenkinsfileAndOtherFiles(pipelineName, otherMap);
  }

  protected void prepRepoWithJenkinsfileAndOtherFiles(
      String pipelineName, Map<String, String> otherFiles) throws Exception {
    sampleRepo.init();
    sampleRepo.write("Jenkinsfile", pipelineSourceFromResources(pipelineName));
    sampleRepo.git("add", "Jenkinsfile");

    for (Map.Entry<String, String> otherFile : otherFiles.entrySet()) {
      if (otherFile != null) {
        sampleRepo.write(otherFile.getValue(), fileContentsFromResources(otherFile.getKey()));
        sampleRepo.git("add", otherFile.getValue());
      }
    }

    sampleRepo.git("commit", "--message=files");
  }

  protected void prepRepoWithJenkinsfileFromString(String jf) throws Exception {
    sampleRepo.init();
    sampleRepo.write("Jenkinsfile", jf);
    sampleRepo.git("add", "Jenkinsfile");

    sampleRepo.git("commit", "--message=files");
  }

  protected void assumeSh() throws Exception {
    Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.NULL);

    if (!localLauncher.isUnix()) {
      try {
        Assume.assumeThat(
            "Running sh command succeeds",
            localLauncher.launch().cmds("sh", "--version").join(),
            is(0));
      } catch (IOException x) {
        Assume.assumeNoException("Have have a shell variant (sh, bash, etc)", x);
      }
    }
  }

  protected <T extends ParameterDefinition> T getParameterOfType(
      List<ParameterDefinition> params, Class<T> c) {
    for (ParameterDefinition p : params) {
      if (c.isInstance(p)) {
        return (T) p;
      }
    }
    return null;
  }
}
