/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import static groovy.lang.GroovyShell.DEFAULT_CODE_BASE;
import static org.codehaus.groovy.control.Phases.CONVERSION;
import static org.junit.Assert.*;

import com.cloudbees.groovy.cps.NonCPS;
import groovy.lang.GroovyClassLoader;
import java.net.URL;
import java.security.CodeSource;
import java.security.cert.Certificate;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractDeclarativeTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.SourceUnitErrorCollector;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.util.ClassUtils;

public class ModelParserExternalTest extends AbstractDeclarativeTest {
  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void parseOutsideJenkins() throws Exception {
    String script = pipelineSourceFromResources("simpleParameters");

    ModelASTPipelineDef model = parse(script);
    assertNotNull(model);
    assertEquals(1, model.getStages().getStages().size());
  }

  /**
   * parse is used to replicate the ModelParser behavior from Converter.groovy outside of Jenkins
   */
  private ModelASTPipelineDef parse(String script) throws Exception {
    CompilerConfiguration cc = GroovySandbox.createBaseCompilerConfiguration();

    ImportCustomizer ic = new ImportCustomizer();
    ic.addStarImports(NonCPS.class.getPackage().getName());
    ic.addStarImports("hudson.model", "jenkins.model");
    cc.addCompilationCustomizers(ic);

    CompilationUnit cu =
        new CompilationUnit(
            cc,
            new CodeSource(new URL("file", "", DEFAULT_CODE_BASE), (Certificate[]) null),
            new GroovyClassLoader(ClassUtils.getDefaultClassLoader()));
    cu.addSource(Converter.getPIPELINE_SCRIPT_NAME(), script);

    final ModelASTPipelineDef[] model = new ModelASTPipelineDef[1];

    cu.addPhaseOperation(
        new CompilationUnit.SourceUnitOperation() {
          @Override
          public void call(SourceUnit source) throws CompilationFailedException {
            if (model[0] == null) {
              ErrorCollector errorCollector = new SourceUnitErrorCollector(source);
              ModelValidator validator = new AlwaysTrueValidator();
              model[0] =
                  new ModelParser(
                          source, null, errorCollector, validator, new DescriptorLookupCache())
                      .parse(true);
            }
          }
        },
        CONVERSION);

    cu.compile(CONVERSION);

    return model[0];
  }
}
