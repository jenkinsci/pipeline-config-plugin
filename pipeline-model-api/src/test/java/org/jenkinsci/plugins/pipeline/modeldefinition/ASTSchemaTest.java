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
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.net.URL;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class ASTSchemaTest {
  @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
  @ClassRule public static JenkinsRule j = new JenkinsRule();

  @Test
  public void doSchema() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    String rawSchema =
        wc.goTo(ASTSchema.AST_SCHEMA_URL + "/json", "application/json")
            .getWebResponse()
            .getContentAsString();
    assertNotNull(rawSchema);
    JSONObject remoteSchema = JSONObject.fromObject(rawSchema);
    assertNotNull(remoteSchema);
    assertFalse(remoteSchema.isEmpty());
    assertFalse(remoteSchema.isNullObject());

    String rawInternalSchema = fileContentsFromResources("ast-schema.json");
    assertNotNull(rawInternalSchema);
    JSONObject internalSchema = JSONObject.fromObject(rawInternalSchema);

    assertNotNull(internalSchema);
    assertFalse(internalSchema.isEmpty());
    assertFalse(internalSchema.isNullObject());

    assertEquals(internalSchema, remoteSchema);
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
}
