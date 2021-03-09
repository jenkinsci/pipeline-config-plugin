/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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

import static org.codehaus.groovy.control.Phases.CONVERSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URL;
import java.security.CodeSource;
import java.security.cert.Certificate;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.jenkinsci.plugins.pipeline.modeldefinition.BaseParserLoaderTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef;
import org.junit.Test;

public class ASTParserUtilsTest extends BaseParserLoaderTest {
  @Test
  public void prettyPrintTransformed() throws Exception {
    URL src = getClass().getResource("/when/whenEnv.groovy");

    // We need to do our own parsing logic here to make sure we do the runtime transformation, and
    // so that we can
    // capture the CompilationUnit for comparison.
    CompilationUnit cu =
        new CompilationUnit(
            Converter.makeCompilerConfiguration(),
            new CodeSource(src, new Certificate[0]),
            Converter.getCompilationClassLoader());
    cu.addSource(src);

    final ModelASTPipelineDef[] model = new ModelASTPipelineDef[1];

    cu.addPhaseOperation(
        new CompilationUnit.SourceUnitOperation() {
          @Override
          public void call(SourceUnit source) throws CompilationFailedException {
            if (model[0] == null) {
              model[0] = new ModelParser(source).parse(false);
            }
          }
        },
        CONVERSION);

    cu.compile(CONVERSION);

    SourceUnit su = cu.iterator().next();
    assertNotNull(su);

    ModuleNode mn = su.getAST();
    assertNotNull(mn);

    // Replace object ids and the like so that we've got consistent output.
    String prettyPrint =
        ASTParserUtils.prettyPrint(mn)
            .replaceAll("DynamicVariable@.*", "DynamicVariable@something")
            .replaceAll("VariableExpression@.*", "VariableExpression@something")
            .replaceAll("(__model__.*?_\\d+)_\\d+__", "$1_something__")
            .replaceAll(
                "ConstantExpression\\[\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12}",
                "ConstantExpression[some-uuid");
    String expected = fileContentsFromResources("prettyPrintTransformedOutput.txt");
    assertEquals(expected.split("\\n"), prettyPrint.split("\\n"));
  }
}
