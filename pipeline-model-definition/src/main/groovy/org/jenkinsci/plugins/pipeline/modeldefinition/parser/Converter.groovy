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

import static groovy.lang.GroovyShell.DEFAULT_CODE_BASE
import static org.codehaus.groovy.control.Phases.CONVERSION

import com.cloudbees.groovy.cps.NonCPS
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jsonschema.exceptions.ProcessingException
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.report.ProcessingReport
import com.github.fge.jsonschema.tree.JsonTree
import com.github.fge.jsonschema.tree.SimpleJsonTree
import com.github.fge.jsonschema.util.JsonLoader
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import java.security.CodeSource
import java.security.cert.Certificate
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
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.DeclarativeValidatorContributor
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator

/**
 * Utilities for converting from/to {@link ModelASTPipelineDef} and raw Pipeline script.
 *
 * @author Andrew Bayer
 */
class Converter {

	static final String PIPELINE_SCRIPT_NAME = "WorkflowScript"

	/**
	 * Validate provided {@link net.sf.json.JSONObject} against the JSON schema.
	 *
	 * @param origJson A {@link net.sf.json.JSONObject}, which will be converted to a Jackson {@link JsonNode} along the way.
	 * @return A {@link ProcessingReport} with the results of the validation.
	 * @throws ProcessingException If an error of high enough severity is detected in processing.
	 */
	static ProcessingReport validateJSONAgainstSchema(JSONObject origJson) throws ProcessingException {
		return validateJSONAgainstSchema(jacksonJSONFromJSONObject(origJson))
	}

	static ProcessingReport validateJSONAgainstSchema(JsonNode jsonNode) throws ProcessingException {
		JsonSchema schema = ASTSchema.getJSONSchema()

		return schema.validate(jsonNode)
	}

	/**
	 * Converts a net.sf.json {@link JSONObject} into a Jackson {@link JsonNode} for use in schema validation.
	 *
	 * @param input A {@link JSONObject}
	 * @return The converted {@link JsonNode}
	 */
	static JsonNode jacksonJSONFromJSONObject(JSONObject input) {
		return JsonLoader.fromString(input.toString())
	}

	static JsonTree jsonTreeFromJSONObject(JSONObject input) {
		return new SimpleJsonTree(jacksonJSONFromJSONObject(input))
	}

	/**
	 * Converts a script at a given URL into {@link ModelASTPipelineDef}
	 *
	 * @param src A URL pointing to a Pipeline script
	 * @param enabledOptionalValidators A list of optional validator classes that should be enabled. Defaults to empty.
	 * @return the converted script
	 */
	static ModelASTPipelineDef urlToPipelineDef(URL src,
			final List<Class<? extends DeclarativeValidatorContributor>> enabledOptionalValidators = []) {
		CompilationUnit cu = new CompilationUnit(
				makeCompilerConfiguration(),
				new CodeSource(src, new Certificate[0]),
				getCompilationClassLoader())
		cu.addSource(src)

		return compilationUnitToPipelineDef(cu, enabledOptionalValidators)
	}

	static GroovyClassLoader getCompilationClassLoader() {
		return CpsThread.current()?.getExecution()?.getShell()?.classLoader ?:
				new GroovyClassLoader(Jenkins.instance.getPluginManager().uberClassLoader)
	}

	/**
	 * Converts a string containing a Pipeline script into {@link ModelASTPipelineDef}
	 *
	 * @param script A string containing a Pipeline script
	 * @param enabledOptionalValidators A list of optional validator classes that should be enabled. Defaults to empty.
	 * @return the converted script
	 */
	static ModelASTPipelineDef scriptToPipelineDef(String script,
			final List<Class<? extends DeclarativeValidatorContributor>> enabledOptionalValidators = []) {
		CompilationUnit cu = new CompilationUnit(
				makeCompilerConfiguration(),
				new CodeSource(new URL("file", "", DEFAULT_CODE_BASE), (Certificate[]) null),
				getCompilationClassLoader())
		cu.addSource(PIPELINE_SCRIPT_NAME, script)

		return compilationUnitToPipelineDef(cu, enabledOptionalValidators)
	}

	static CompilerConfiguration makeCompilerConfiguration() {
		CompilerConfiguration cc = GroovySandbox.createBaseCompilerConfiguration()

		ImportCustomizer ic = new ImportCustomizer()
		ic.addStarImports(NonCPS.class.getPackage().getName())
		ic.addStarImports("hudson.model","jenkins.model")
		for (GroovyShellDecorator d : GroovyShellDecorator.all()) {
			d.customizeImports(null, ic)
		}

		cc.addCompilationCustomizers(ic)

		return cc
	}

	/**
	 * Takes a {@link CompilationUnit}, compiles it with the {@link ModelParser} injected, and returns the resulting
	 * {@link ModelASTPipelineDef}
	 *
	 * @param cu {@link CompilationUnit} assembled by another method.
	 * @param enabledOptionalValidators A list of optional validator classes that should be enabled. Defaults to empty.
	 * @return The converted script
	 */
	@SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD")
	static ModelASTPipelineDef compilationUnitToPipelineDef(CompilationUnit cu,
			final List<Class<? extends DeclarativeValidatorContributor>> enabledOptionalValidators = []) {
		final ModelASTPipelineDef[] model = new ModelASTPipelineDef[1]

		cu.addPhaseOperation(new CompilationUnit.SourceUnitOperation() {
					@Override
					void call(SourceUnit source) throws CompilationFailedException {
						if (model[0] == null) {
							model[0] = new ModelParser(source, enabledOptionalValidators).parse(true)
						}
					}
				}, CONVERSION)

		cu.compile(CONVERSION)

		return model[0]
	}

	static List<ModelASTStep> scriptToPlainSteps(String script,
			final List<Class<? extends DeclarativeValidatorContributor>> enabledOptionalValidators = []) {
		CompilationUnit cu = new CompilationUnit(
				makeCompilerConfiguration(),
				new CodeSource(new URL("file", "", DEFAULT_CODE_BASE), (Certificate[]) null),
				getCompilationClassLoader())
		cu.addSource(PIPELINE_SCRIPT_NAME, script)

		return compilationUnitToPlainSteps(cu, enabledOptionalValidators)
	}

	@SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD")
	private static List<ModelASTStep> compilationUnitToPlainSteps(CompilationUnit cu,
			final List<Class<? extends DeclarativeValidatorContributor>> enabledOptionalValidators = []) {
		final List<ModelASTStep>[] model = new List<ModelASTStep>[1]

		cu.addPhaseOperation(new CompilationUnit.SourceUnitOperation() {
					@Override
					void call(SourceUnit source) throws CompilationFailedException {
						if (model[0] == null) {
							model[0] = new ModelParser(source, enabledOptionalValidators).parsePlainSteps(source.AST)
						}
					}
				}, CONVERSION)

		cu.compile(CONVERSION)

		return model[0]
	}
}
