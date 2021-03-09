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
package org.jenkinsci.plugins.pipeline.modeldefinition.endpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.BaseParserLoaderTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.JSONParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

@RunWith(Parameterized.class)
public class SuccessfulEndpointOpsTest extends AbstractModelDefTest {
  private String configName;

  public SuccessfulEndpointOpsTest(String configName) {
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
  public void testSuccessfulValidateJson() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath()
                    + ModelConverterAction.PIPELINE_CONVERTER_URL
                    + "/validateJson"),
            HttpMethod.POST);
    String simpleJson = fileContentsFromResources("json/" + configName + ".json");

    assertNotNull(simpleJson);

    NameValuePair pair = new NameValuePair("json", simpleJson);
    req.setRequestParameters(Collections.singletonList(pair));

    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);
    assertEquals(
        "Result wasn't successful - " + result.toString(2),
        "success",
        resultData.getString("result"));
  }

  @Test
  public void testSuccessfulValidateJenkinsfile() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath()
                    + ModelConverterAction.PIPELINE_CONVERTER_URL
                    + "/validateJenkinsfile"),
            HttpMethod.POST);
    String rawJenkinsfile = fileContentsFromResources(configName + ".groovy");

    assertNotNull(rawJenkinsfile);

    NameValuePair pair = new NameValuePair("jenkinsfile", rawJenkinsfile);
    req.setRequestParameters(Collections.singletonList(pair));

    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);
    assertEquals(
        "Result wasn't successful - " + result.toString(2),
        "success",
        resultData.getString("result"));
  }

  @Test
  public void testSuccessfulToJenkinsfile() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath()
                    + ModelConverterAction.PIPELINE_CONVERTER_URL
                    + "/toJenkinsfile"),
            HttpMethod.POST);
    String simpleJson = fileContentsFromResources("json/" + configName + ".json");

    assertNotNull(simpleJson);

    NameValuePair pair = new NameValuePair("json", simpleJson);
    req.setRequestParameters(Collections.singletonList(pair));

    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);

    String rawGroovy = resultData.optString("jenkinsfile", null);
    assertNotNull(resultData.toString(2), rawGroovy);

    ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(rawGroovy);
    assertNotNull(pipelineDef);
  }

  @Test
  public void testSuccessfulToJson() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/toJson"),
            HttpMethod.POST);
    String initialGroovy = fileContentsFromResources(configName + ".groovy");

    assertNotNull(initialGroovy);

    NameValuePair pair = new NameValuePair("jenkinsfile", initialGroovy);
    req.setRequestParameters(Collections.singletonList(pair));

    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);
    assertEquals(
        "Result wasn't successful - " + result.toString(2),
        "success",
        resultData.getString("result"));

    assertNotNull(resultData.getString("json"));
    JSONObject rawJson = JSONObject.fromObject(resultData.getString("json"));
    JSONParser p = new JSONParser(Converter.jsonTreeFromJSONObject(rawJson));

    ModelASTPipelineDef pipelineDef = p.parse();

    assertNotNull(pipelineDef);
    assertEquals(
        "Errors found: " + BaseParserLoaderTest.getJSONErrorReport(p, configName),
        0,
        p.getErrorCollector().getErrorCount());
  }
}
