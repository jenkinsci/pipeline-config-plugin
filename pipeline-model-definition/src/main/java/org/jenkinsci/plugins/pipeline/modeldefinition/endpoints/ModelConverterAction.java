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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.tree.SimpleJsonTree;
import com.github.fge.jsonschema.util.JsonLoader;
import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.security.csrf.CrumbExclusion;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.JSONParser;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Endpoint for converting to/from JSON/Groovy and validating both.
 *
 * @author Andrew Bayer
 */
@Extension
public class ModelConverterAction implements RootAction {
  public static final String PIPELINE_CONVERTER_URL = "pipeline-model-converter";

  @Override
  public String getUrlName() {
    return PIPELINE_CONVERTER_URL;
  }

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @SuppressWarnings("unused")
  @RequirePOST
  public HttpResponse doToJenkinsfile(StaplerRequest req) {
    Jenkins.get().checkPermission(Jenkins.READ);

    JSONObject result = new JSONObject();

    String jsonAsString = req.getParameter("json");

    if (!StringUtils.isEmpty(jsonAsString)) {
      try {
        JsonNode json = JsonLoader.fromString(jsonAsString);

        JSONParser parser = new JSONParser(new SimpleJsonTree(json));

        ModelASTPipelineDef pipelineDef = parser.parse();

        if (pipelineDef != null && !collectErrors(result, parser.getErrorCollector())) {
          try {
            Converter.scriptToPipelineDef(pipelineDef.toPrettyGroovy());
            result.accumulate("result", "success");
            result.accumulate("jenkinsfile", pipelineDef.toPrettyGroovy());
          } catch (Exception e) {
            JSONObject jfErrors = new JSONObject();
            reportFailure(jfErrors, e);
            JSONArray errors = new JSONArray();
            errors.add(new JSONObject().accumulate("jenkinsfileErrors", jfErrors));
            reportFailure(result, errors);
          }
        }
      } catch (Exception je) {
        reportFailure(result, je);
      }
    } else {
      reportFailure(result, "No content found for 'json' parameter");
    }

    return HttpResponses.okJSON(result);
  }

  @SuppressWarnings("unused")
  @RequirePOST
  public HttpResponse doToJson(StaplerRequest req) {
    Jenkins.get().checkPermission(Jenkins.READ);

    JSONObject result = new JSONObject();

    String groovyAsString = req.getParameter("jenkinsfile");

    if (!StringUtils.isEmpty(groovyAsString)) {
      try {
        ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
        if (pipelineDef != null) {
          result.accumulate("result", "success");
          result.accumulate("json", pipelineDef.toJSON());
        } else {
          reportFailure(
              result,
              "Jenkinsfile content '" + groovyAsString + "' did not contain the 'pipeline' step");
        }
      } catch (Exception e) {
        reportFailure(result, e);
      }
    } else {
      reportFailure(result, "No content found for 'jenkinsfile' parameter");
    }

    return HttpResponses.okJSON(result);
  }

  @SuppressWarnings("unused")
  @RequirePOST
  public HttpResponse doStepsToJson(StaplerRequest req) {
    Jenkins.get().checkPermission(Jenkins.READ);

    JSONObject result = new JSONObject();

    String groovyAsString = req.getParameter("jenkinsfile");

    if (!StringUtils.isEmpty(groovyAsString)) {
      try {
        List<ModelASTStep> steps = Converter.scriptToPlainSteps(groovyAsString);
        JSONArray array = new JSONArray();
        for (ModelASTStep step : steps) {
          array.add(step.toJSON());
        }
        result.accumulate("result", "success");
        result.accumulate("json", array);
      } catch (Exception e) {
        reportFailure(result, e);
      }
    } else {
      reportFailure(result, "No content found for 'jenkinsfile' parameter");
    }

    return HttpResponses.okJSON(result);
  }

  @SuppressWarnings("unused")
  @RequirePOST
  public HttpResponse doStepsToJenkinsfile(StaplerRequest req) {
    Jenkins.get().checkPermission(Jenkins.READ);

    JSONObject result = new JSONObject();

    String jsonAsString = req.getParameter("json");
    if (!StringUtils.isEmpty(jsonAsString)) {
      try {
        JsonNode json = JsonLoader.fromString(jsonAsString);

        List<JsonNode> jsonSteps = new ArrayList<>();
        if (json.isArray()) {
          jsonSteps.addAll(ImmutableList.copyOf(json.elements()));
        } else {
          jsonSteps.add(json);
        }
        JSONParser parser = new JSONParser(null);
        List<ModelASTStep> astSteps = new ArrayList<>(jsonSteps.size());
        for (JsonNode jsonStep : jsonSteps) {
          if (!jsonStep.isObject()) {
            continue;
          }
          ModelASTStep astStep = parser.parseStep(new SimpleJsonTree(jsonStep));
          if (astStep != null) {
            astStep.validate(parser.getValidator());
            astSteps.add(astStep);
          }
        }

        boolean collectedSomeErrors = collectErrors(result, parser.getErrorCollector());

        if (!collectedSomeErrors && astSteps.isEmpty()) {
          reportFailure(result, "No result.");
        } else if (!collectedSomeErrors) {
          result.accumulate("result", "success");
          StringBuilder jenkinsFile = new StringBuilder();
          for (ModelASTStep step : astSteps) {
            if (jenkinsFile.length() > 0) {
              jenkinsFile.append('\n');
            }
            jenkinsFile.append(step.toGroovy());
          }
          result.accumulate("jenkinsfile", jenkinsFile.toString());
        }
      } catch (Exception je) {
        reportFailure(result, je);
      }
    } else {
      reportFailure(result, "No content found for 'json' parameter");
    }

    return HttpResponses.okJSON(result);
  }

  @SuppressWarnings("unused")
  @RequirePOST
  public HttpResponse doValidateJenkinsfile(StaplerRequest req) {
    Jenkins.get().checkPermission(Jenkins.READ);

    JSONObject result = new JSONObject();

    String groovyAsString = req.getParameter("jenkinsfile");

    if (!StringUtils.isEmpty(groovyAsString)) {
      try {
        ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(groovyAsString);
        if (pipelineDef != null) {
          result.accumulate("result", "success");
        } else {
          reportFailure(
              result,
              "Jenkinsfile content '" + groovyAsString + "' did not contain the 'pipeline' step");
        }

      } catch (Exception e) {
        reportFailure(result, e);
      }
    } else {
      reportFailure(result, "No content found for 'jenkinsfile' parameter");
    }

    return HttpResponses.okJSON(result);
  }

  @SuppressWarnings("unused")
  @RequirePOST
  public HttpResponse doValidateJson(StaplerRequest req) {
    Jenkins.get().checkPermission(Jenkins.READ);

    JSONObject result = new JSONObject();

    String jsonAsString = req.getParameter("json");
    if (!StringUtils.isEmpty(jsonAsString)) {
      try {

        JsonNode json = JsonLoader.fromString(jsonAsString);

        JSONParser parser = new JSONParser(new SimpleJsonTree(json));

        ModelASTPipelineDef pipelineDef = parser.parse();

        if (pipelineDef != null) {
          if (!collectErrors(result, parser.getErrorCollector()) && result.isEmpty()) {
            try {
              Converter.scriptToPipelineDef(pipelineDef.toPrettyGroovy());
              result.accumulate("result", "success");
            } catch (Exception e) {
              JSONObject jfErrors = new JSONObject();
              reportFailure(jfErrors, e);
              JSONArray errors = new JSONArray();
              errors.add(new JSONObject().accumulate("jenkinsfileErrors", jfErrors));
              reportFailure(result, errors);
            }
          }
        }
      } catch (Exception je) {
        reportFailure(result, je);
      }
    } else {
      reportFailure(result, "No content found for 'json' parameter");
    }

    return HttpResponses.okJSON(result);
  }

  @SuppressWarnings("unused")
  @RequirePOST
  public HttpResponse doValidate(StaplerRequest req) {
    Jenkins.get().checkPermission(Jenkins.READ);

    List<String> output = new ArrayList<>();

    String groovyAsString = req.getParameter("jenkinsfile");

    if (groovyAsString != null) {
      try {
        if (Converter.scriptToPipelineDef(groovyAsString) != null) {
          output.add("Jenkinsfile successfully validated.");
        } else {
          output.add(
              "Jenkinsfile content '" + groovyAsString + "' did not contain the 'pipeline' step");
        }
      } catch (Exception e) {
        output.add("Errors encountered validating Jenkinsfile:");
        output.addAll(ModelConverterAction.errorToStrings(e));
      }
    } else {
      output.add("No Jenkinsfile specified");
    }

    return HttpResponses.plainText(StringUtils.join(output, "\n"));
  }

  public static List<String> errorToStrings(Exception e) {
    List<String> output = new ArrayList<>();
    if (e instanceof MultipleCompilationErrorsException) {
      MultipleCompilationErrorsException ce = (MultipleCompilationErrorsException) e;
      for (Object o : ce.getErrorCollector().getErrors()) {
        if (o instanceof SyntaxErrorMessage) {
          SyntaxErrorMessage s = (SyntaxErrorMessage) o;
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          s.write(pw);
          pw.close();
          output.add(sw.toString());
        }
      }
    } else {
      output.add(e.getMessage());
    }

    return output;
  }
  /**
   * Checks the error collector for errors, and if there are any set the result as failure
   *
   * @param result the result to mutate if so
   * @param errorCollector the collector of errors
   * @return {@code true} if any errors where collected.
   */
  private boolean collectErrors(JSONObject result, ErrorCollector errorCollector) {
    if (errorCollector.getErrorCount() > 0) {
      JSONArray errors = errorCollector.asJson();
      reportFailure(result, errors);
      return true;
    }
    return false;
  }

  /**
   * Report result to be a failure message due to the given exception.
   *
   * @param result the result to mutate
   * @param e the exception to report
   */
  private void reportFailure(JSONObject result, Exception e) {
    JSONArray errors = new JSONArray();
    JSONObject j = new JSONObject();

    if (e instanceof MultipleCompilationErrorsException) {
      MultipleCompilationErrorsException ce = (MultipleCompilationErrorsException) e;
      for (Object o : ce.getErrorCollector().getErrors()) {
        if (o instanceof SyntaxErrorMessage) {
          j.accumulate("error", ((SyntaxErrorMessage) o).getCause().getMessage());
        }
      }
    } else {
      j.accumulate("error", e.getMessage());
    }
    errors.add(j);
    reportFailure(result, errors);
  }

  /**
   * Report result to be a failure message due to the given error message.
   *
   * @param result the result to mutate
   * @param message the error
   */
  private void reportFailure(JSONObject result, String message) {
    JSONArray errors = new JSONArray();
    JSONObject o = new JSONObject();
    o.accumulate("error", message);
    errors.add(o);
    reportFailure(result, errors);
  }

  /**
   * Report result to be a failure message due to the given error messages.
   *
   * @param result the result to mutate
   * @param errors the errors
   */
  private void reportFailure(JSONObject result, JSONArray errors) {
    result.accumulate("result", "failure");
    result.accumulate("errors", errors);
  }

  @Extension
  public static class ModelConverterActionCrumbExclusion extends CrumbExclusion {
    @Override
    public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
        throws IOException, ServletException {
      String pathInfo = req.getPathInfo();

      if (pathInfo != null && pathInfo.startsWith("/" + PIPELINE_CONVERTER_URL + "/")) {
        chain.doFilter(req, resp);
        return true;
      }

      return false;
    }
  }
}
