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

import static jenkins.model.Jenkins.READ;

import hudson.Extension;
import hudson.cli.CLICommand;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.endpoints.ModelConverterAction;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter;

@Extension
public class DeclarativeLinterCommand extends CLICommand {
  @Override
  public String getShortDescription() {
    return Messages.DeclarativeLinterCommand_ShortDescription();
  }

  protected int run() throws Exception {
    Jenkins.get().checkPermission(READ);
    int retVal = 0;
    List<String> output = new ArrayList<>();

    String script = IOUtils.toString(stdin);

    if (script != null) {
      try {

        ModelASTPipelineDef pipelineDef = Converter.scriptToPipelineDef(script);
        if (pipelineDef != null) {
          output.add("Jenkinsfile successfully validated.");
          retVal = 0;
        } else {
          output.add("Jenkinsfile content '" + script + "' did not contain the 'pipeline' step");
          retVal = 1;
        }
      } catch (Exception e) {
        output.add("Errors encountered validating Jenkinsfile:");
        retVal = 1;
        output.addAll(ModelConverterAction.errorToStrings(e));
      }
    }

    IOUtils.writeLines(output, null, stdout);

    return retVal;
  }
}
