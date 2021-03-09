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
package org.jenkinsci.plugins.pipeline.modeldefinition.validator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JSONSchemaTest extends AbstractModelDefTest {

  private String configName;

  public JSONSchemaTest(String configName) {
    this.configName = configName;
  }

  @Parameterized.Parameters(name = "Name: {0}")
  public static Iterable<Object[]> generateParameters() {
    List<Object[]> result = new ArrayList<>();
    for (String c : AbstractModelDefTest.SHOULD_PASS_CONFIGS) {
      result.add(new Object[] {c});
    }
    for (String c : AbstractModelDefTest.CONVERT_ONLY_SHOULD_PASS_CONFIGS) {
      result.add(new Object[] {c});
    }

    return result;
  }

  @Test
  public void validateAgainstSchema() throws Exception {
    ModelASTPipelineDef root =
        Converter.urlToPipelineDef(getClass().getResource("/" + configName + ".groovy"));

    assertNotNull(root);

    JSONObject origJson = root.toJSON();
    assertNotNull(origJson);

    ProcessingReport report = Converter.validateJSONAgainstSchema(origJson);
    assertTrue(getErrorReport(origJson, configName, report), report.isSuccess());
  }

  private String getErrorReport(JSONObject node, String configName, ProcessingReport r) {
    StringBuilder b = new StringBuilder();
    b.append("Config name: ");
    b.append(configName);
    b.append("\n");

    b.append("JSON: ");
    b.append(node.toString(2));
    b.append("\n");

    for (ProcessingMessage m : r) {
      b.append("Message: ");
      b.append(m.getMessage());
      b.append("\n");
      b.append("Error: ");
      b.append(m.toString());
      b.append("\n");
    }

    return b.toString();
  }
}
