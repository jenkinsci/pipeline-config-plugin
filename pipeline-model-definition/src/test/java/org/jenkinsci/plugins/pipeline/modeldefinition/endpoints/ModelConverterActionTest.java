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

import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import java.net.URL;
import java.util.Collections;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Tools;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ModelConverterActionTest extends AbstractModelDefTest {

  @Test
  public void toJsonEmptyParam() throws Exception {
    getExpectedErrorNoParam("jenkinsfile", "toJson");
  }

  @Test
  public void validateJsonEmptyParam() throws Exception {
    getExpectedErrorNoParam("json", "validateJson");
  }

  @Test
  public void toJenkinsfileEmptyParam() throws Exception {
    getExpectedErrorNoParam("json", "toJenkinsfile");
  }

  @Test
  public void validateJenkinsfileEmptyParam() throws Exception {
    getExpectedErrorNoParam("jenkinsfile", "validateJenkinsfile");
  }

  private void getExpectedErrorNoParam(String param, String endpoint) throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath() + ModelConverterAction.PIPELINE_CONVERTER_URL + "/" + endpoint),
            HttpMethod.POST);
    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    // TODO: Change this when we get proper JSON errors causing HTTP error codes
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);
    assertEquals(
        "Result wasn't a failure - " + result.toString(2),
        "failure",
        resultData.getString("result"));

    String expectedError = "No content found for '" + param + "' parameter";
    assertTrue(
        "Errors array ("
            + resultData.getJSONArray("errors").toString(2)
            + ") didn't contain expected error '"
            + expectedError
            + "'",
        foundExpectedErrorInJSON(resultData.getJSONArray("errors"), expectedError));
  }

  @Test
  public void errorOnNoPipeline() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath()
                    + ModelConverterAction.PIPELINE_CONVERTER_URL
                    + "/validateJenkinsfile"),
            HttpMethod.POST);
    String groovyAsString = "echo 'nothing to see here'";
    NameValuePair pair = new NameValuePair("jenkinsfile", groovyAsString);
    req.setRequestParameters(Collections.singletonList(pair));

    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    // TODO: Change this when we get proper JSON errors causing HTTP error codes
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);
    assertEquals(
        "Result wasn't a failure - " + result.toString(2),
        "failure",
        resultData.getString("result"));

    String expectedError =
        "Jenkinsfile content '" + groovyAsString + "' did not contain the 'pipeline' step";
    assertTrue(
        "Errors array ("
            + resultData.getJSONArray("errors").toString(2)
            + ") didn't contain expected error '"
            + expectedError
            + "'",
        foundExpectedErrorInJSON(resultData.getJSONArray("errors"), expectedError));
  }

  @Test
  public void testFailedValidateJsonInvalidBuildCondition() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath()
                    + ModelConverterAction.PIPELINE_CONVERTER_URL
                    + "/validateJson"),
            HttpMethod.POST);
    String simpleJson = fileContentsFromResources("json/errors/invalidBuildCondition.json");

    assertNotNull(simpleJson);

    NameValuePair pair = new NameValuePair("json", simpleJson);
    req.setRequestParameters(Collections.singletonList(pair));

    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    // TODO: Change this when we get proper JSON errors causing HTTP error codes
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);
    assertEquals(
        "Result wasn't a failure - " + result.toString(2),
        "failure",
        resultData.getString("result"));

    String expectedError =
        Messages.ModelValidatorImpl_InvalidBuildCondition(
            "banana", BuildCondition.getOrderedConditionNames());
    assertTrue(
        "Errors array ("
            + resultData.getJSONArray("errors").toString(2)
            + ") didn't contain expected error '"
            + expectedError
            + "'",
        foundExpectedErrorInJSON(resultData.getJSONArray("errors"), expectedError));
  }

  @Test
  public void testFailedValidateJenkinsfileInvalidBuildCondition() throws Exception {
    final String rawJenkinsfile =
        fileContentsFromResources("errors/invalidBuildCondition.groovy", true);
    // TODO: Get a better approach for skipping JSON-specific errors
    if (rawJenkinsfile != null) {

      JenkinsRule.WebClient wc = j.createWebClient();
      WebRequest req =
          new WebRequest(
              new URL(
                  wc.getContextPath()
                      + ModelConverterAction.PIPELINE_CONVERTER_URL
                      + "/validateJenkinsfile"),
              HttpMethod.POST);

      assertNotNull(rawJenkinsfile);

      NameValuePair pair = new NameValuePair("jenkinsfile", rawJenkinsfile);
      req.setRequestParameters(Collections.singletonList(pair));

      String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
      assertNotNull(rawResult);

      JSONObject result = JSONObject.fromObject(rawResult);
      // TODO: Change this when we get proper JSON errors causing HTTP error codes
      assertEquals(
          "Full result doesn't include status - " + result.toString(2),
          "ok",
          result.getString("status"));
      JSONObject resultData = result.getJSONObject("data");
      assertNotNull(resultData);
      assertEquals(
          "Result wasn't a failure - " + result.toString(2),
          "failure",
          resultData.getString("result"));
    }
  }

  @Test
  public void testFailedValidateJsonUnlistedToolType() throws Exception {
    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath()
                    + ModelConverterAction.PIPELINE_CONVERTER_URL
                    + "/validateJson"),
            HttpMethod.POST);
    String simpleJson = fileContentsFromResources("json/errors/unlistedToolType.json");

    assertNotNull(simpleJson);

    NameValuePair pair = new NameValuePair("json", simpleJson);
    req.setRequestParameters(Collections.singletonList(pair));

    String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
    assertNotNull(rawResult);

    JSONObject result = JSONObject.fromObject(rawResult);
    // TODO: Change this when we get proper JSON errors causing HTTP error codes
    assertEquals(
        "Full result doesn't include status - " + result.toString(2),
        "ok",
        result.getString("status"));
    JSONObject resultData = result.getJSONObject("data");
    assertNotNull(resultData);
    assertEquals(
        "Result wasn't a failure - " + result.toString(2),
        "failure",
        resultData.getString("result"));

    String expectedError =
        Messages.ModelValidatorImpl_InvalidSectionType(
            "tool", "banana", Tools.getAllowedToolTypes().keySet());

    assertTrue(
        "Errors array ("
            + resultData.getJSONArray("errors").toString(2)
            + ") didn't contain expected error '"
            + expectedError
            + "'",
        foundExpectedErrorInJSON(resultData.getJSONArray("errors"), expectedError));
  }

  @Test
  public void testFailedValidateJenkinsfileUnlistedToolType() throws Exception {
    final String rawJenkinsfile = fileContentsFromResources("errors/unlistedToolType.groovy", true);
    // TODO: Get a better approach for skipping JSON-specific errors
    if (rawJenkinsfile != null) {

      JenkinsRule.WebClient wc = j.createWebClient();
      WebRequest req =
          new WebRequest(
              new URL(
                  wc.getContextPath()
                      + ModelConverterAction.PIPELINE_CONVERTER_URL
                      + "/validateJenkinsfile"),
              HttpMethod.POST);

      assertNotNull(rawJenkinsfile);

      NameValuePair pair = new NameValuePair("jenkinsfile", rawJenkinsfile);
      req.setRequestParameters(Collections.singletonList(pair));

      String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
      assertNotNull(rawResult);

      JSONObject result = JSONObject.fromObject(rawResult);
      // TODO: Change this when we get proper JSON errors causing HTTP error codes
      assertEquals(
          "Full result doesn't include status - " + result.toString(2),
          "ok",
          result.getString("status"));
      JSONObject resultData = result.getJSONObject("data");
      assertNotNull(resultData);
      assertEquals(
          "Result wasn't a failure - " + result.toString(2),
          "failure",
          resultData.getString("result"));
    }
  }

  @Test
  public void testInvalidScriptContentsInJson() throws Exception {
    final String simpleJson =
        fileContentsFromResources("json/errors/invalidScriptContents.json", true);

    JenkinsRule.WebClient wc = j.createWebClient();
    WebRequest req =
        new WebRequest(
            new URL(
                wc.getContextPath()
                    + ModelConverterAction.PIPELINE_CONVERTER_URL
                    + "/toJenkinsfile"),
            HttpMethod.POST);

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

    assertEquals("failure", resultData.getString("result"));

    JSONArray topErrors = resultData.getJSONArray("errors");
    assertNotNull(topErrors);
    assertEquals(1, topErrors.size());

    String expectedError =
        Messages.ModelValidatorImpl_CompilationErrorInCodeBlock("script", "unexpected token: ");

    assertTrue(
        "Errors array ("
            + topErrors.toString(2)
            + ") didn't contain expected error '"
            + expectedError
            + "'",
        foundExpectedErrorInJSON(topErrors, expectedError));
  }

  @Issue("SECURITY-1266")
  @Test
  public void testLocalASTTransformInCompilation() throws Exception {
    final String rawJenkinsfile =
        fileContentsFromResources("localASTTransformInCompilation.groovy", true);
    // TODO: Get a better approach for skipping JSON-specific errors
    if (rawJenkinsfile != null) {

      JenkinsRule.WebClient wc = j.createWebClient();
      WebRequest req =
          new WebRequest(
              new URL(
                  wc.getContextPath()
                      + ModelConverterAction.PIPELINE_CONVERTER_URL
                      + "/validateJenkinsfile"),
              HttpMethod.POST);

      assertNotNull(rawJenkinsfile);

      NameValuePair pair = new NameValuePair("jenkinsfile", rawJenkinsfile);
      req.setRequestParameters(Collections.singletonList(pair));

      String rawResult = wc.getPage(req).getWebResponse().getContentAsString();
      assertNotNull(rawResult);

      JSONObject result = JSONObject.fromObject(rawResult);
      // TODO: Change this when we get proper JSON errors causing HTTP error codes
      assertEquals(
          "Full result doesn't include status - " + result.toString(2),
          "ok",
          result.getString("status"));
      JSONObject resultData = result.getJSONObject("data");
      assertNotNull(resultData);
      assertEquals(
          "Result wasn't a failure - " + result.toString(2),
          "failure",
          resultData.getString("result"));

      assertNull(j.jenkins.getItem("should-not-exist"));
    }
  }
}
