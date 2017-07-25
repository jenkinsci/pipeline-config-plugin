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
package org.jenkinsci.plugins.pipeline.modeldefinition.parser

import com.cloudbees.groovy.cps.NonCPS
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.util.JsonLoader
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.fasterxml.jackson.databind.JsonNode
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.exceptions.ProcessingException
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.main.JsonSchema
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.report.ProcessingReport
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.tree.JsonTree
import org.jenkinsci.plugins.pipeline.modeldefinition.shaded.com.github.fge.jsonschema.tree.SimpleJsonTree
import jenkins.model.Jenkins
import net.sf.json.JSONObject
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.jenkinsci.plugins.pipeline.modeldefinition.ASTSchema
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator
import org.jenkinsci.plugins.workflow.job.WorkflowRun

import java.security.CodeSource
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

import static groovy.lang.GroovyShell.DEFAULT_CODE_BASE
import static org.codehaus.groovy.control.Phases.CANONICALIZATION

/**
 * Utilities for converting from/to {@link ModelASTPipelineDef} and raw Pipeline script.
 *
 * @author Andrew Bayer
 */
public class Converter {

    public static final String PIPELINE_SCRIPT_NAME = "WorkflowScript"

    /**
     * Validate provided {@link net.sf.json.JSONObject} against the JSON schema.
     *
     * @param origJson A {@link net.sf.json.JSONObject}, which will be converted to a Jackson {@link JsonNode} along the way.
     * @return A {@link ProcessingReport} with the results of the validation.
     * @throws ProcessingException If an error of high enough severity is detected in processing.
     */
    public static ProcessingReport validateJSONAgainstSchema(JSONObject origJson) throws ProcessingException {
        return validateJSONAgainstSchema(jacksonJSONFromJSONObject(origJson))
    }

    public static ProcessingReport validateJSONAgainstSchema(JsonNode jsonNode) throws ProcessingException {
        JsonSchema schema = ASTSchema.getJSONSchema();

        return schema.validate(jsonNode)
    }

    /**
     * Converts a net.sf.json {@link JSONObject} into a Jackson {@link JsonNode} for use in schema validation.
     *
     * @param input A {@link JSONObject}
     * @return The converted {@link JsonNode}
     */
    public static JsonNode jacksonJSONFromJSONObject(JSONObject input) {
        return JsonLoader.fromString(input.toString())
    }

    public static JsonTree jsonTreeFromJSONObject(JSONObject input) {
        return new SimpleJsonTree(jacksonJSONFromJSONObject(input))
    }

    /**
     * Converts a script at a given URL into {@link ModelASTPipelineDef}
     *
     * @param src A URL pointing to a Pipeline script
     * @return the converted script
     */
    public static ModelASTPipelineDef urlToPipelineDef(URL src) {
        CompilationUnit cu = new CompilationUnit(
            makeCompilerConfiguration(),
            new CodeSource(src, new Certificate[0]),
            getCompilationClassLoader());
        cu.addSource(src)

        return compilationUnitToPipelineDef(cu)
    }

    private static GroovyClassLoader getCompilationClassLoader() {
        return CpsThread.current()?.getExecution()?.getShell()?.classLoader ?:
            new GroovyClassLoader(Jenkins.instance.getPluginManager().uberClassLoader)
    }

    /**
     * Converts a string containing a Pipeline script into {@link ModelASTPipelineDef}
     *
     * @param script A string containing a Pipeline script
     * @return the converted script
     */
    public static ModelASTPipelineDef scriptToPipelineDef(String script) {
        CompilationUnit cu = new CompilationUnit(
            makeCompilerConfiguration(),
            new CodeSource(new URL("file", "", DEFAULT_CODE_BASE), (Certificate[]) null),
            getCompilationClassLoader());
        cu.addSource(PIPELINE_SCRIPT_NAME, script)

        return compilationUnitToPipelineDef(cu)
    }

    private static CompilerConfiguration makeCompilerConfiguration() {
        CompilerConfiguration cc = new CompilerConfiguration();

        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model","jenkins.model");
        for (GroovyShellDecorator d : GroovyShellDecorator.all()) {
            d.customizeImports(null, ic);
        }

        cc.addCompilationCustomizers(ic);

        return cc;
    }
    /**
     * Takes a {@link CompilationUnit}, copmiles it with the {@link ModelParser} injected, and returns the resulting
     * {@link ModelASTPipelineDef}
     *
     * @param cu {@link CompilationUnit} assembled by another method.
     * @return The converted script
     */
    private static ModelASTPipelineDef compilationUnitToPipelineDef(CompilationUnit cu) {
        final ModelASTPipelineDef[] model = new ModelASTPipelineDef[1];

        cu.addPhaseOperation(new CompilationUnit.SourceUnitOperation() {
            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                if (model[0] == null) {
                    model[0] = new ModelParser(source).parse(true);
                }
            }
        }, CANONICALIZATION);

        cu.compile(CANONICALIZATION);

        return model[0];
    }

    public static List<ModelASTStep> scriptToPlainSteps(String script) {
        CompilationUnit cu = new CompilationUnit(
            makeCompilerConfiguration(),
            new CodeSource(new URL("file", "", DEFAULT_CODE_BASE), (Certificate[]) null),
            getCompilationClassLoader());
        cu.addSource(PIPELINE_SCRIPT_NAME, script)

        return compilationUnitToPlainSteps(cu)
    }

    private static List<ModelASTStep> compilationUnitToPlainSteps(CompilationUnit cu) {
        final List<ModelASTStep>[] model = new List<ModelASTStep>[1];

        cu.addPhaseOperation(new CompilationUnit.SourceUnitOperation() {
            @Override
            public void call(SourceUnit source) throws CompilationFailedException {
                if (model[0] == null) {
                    model[0] = new ModelParser(source).parsePlainSteps(source.AST);
                }
            }
        }, CANONICALIZATION);

        cu.compile(CANONICALIZATION);

        return model[0];
    }

    /**
     * Converts the raw script from a {@link WorkflowRun} into {@link ModelASTPipelineDef}
     *
     * @param run The {@link WorkflowRun} to pull from.
     * @return A parsed and validated {@link ModelASTPipelineDef}
     */
    public static ModelASTPipelineDef parseFromWorkflowRun(WorkflowRun run) throws Exception {
        CpsFlowExecution execution = null
        if (run.execution != null) {
            execution = (CpsFlowExecution) run.execution
        } else if (run.getExecutionPromise() != null) {
            execution = (CpsFlowExecution) run.getExecutionPromise().get(2, TimeUnit.SECONDS)
        }
        return scriptToPipelineDef(execution.script)
    }

}
