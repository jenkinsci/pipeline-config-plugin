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

import com.github.fge.jsonschema.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import hudson.Extension;
import hudson.model.RootAction;
import java.io.IOException;
import javax.servlet.ServletException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Endpoint for exposing the AST JSON schema.
 *
 * @author Andrew Bayer
 */
@Extension
public class ASTSchema implements RootAction {
  public static final String AST_SCHEMA_URL = "pipeline-model-schema";

  @Override
  public String getUrlName() {
    return AST_SCHEMA_URL;
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
  public void doJson(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
    rsp.serveFile(req, getClass().getResource("/ast-schema.json"));
  }

  /**
   * Get the Pipeline Config AST JSON schema.
   *
   * @return the schema in {@link JsonSchema} form.
   * @throws ProcessingException if there are issues reading the schema
   */
  public static JsonSchema getJSONSchema() throws ProcessingException {
    final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
    return factory.getJsonSchema("resource:/ast-schema.json");
  }
}
