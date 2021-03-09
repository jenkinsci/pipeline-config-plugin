/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils.*
import static org.objectweb.asm.Opcodes.ACC_PUBLIC

import edu.umd.cs.findbugs.annotations.CheckForNull
import edu.umd.cs.findbugs.annotations.NonNull
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.model.JobProperty
import hudson.model.ParameterDefinition
import hudson.model.Run
import hudson.triggers.Trigger
import java.util.stream.Collectors
import jenkins.util.SystemProperties
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.DynamicVariable
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Types
import org.jenkinsci.plugins.pipeline.modeldefinition.RuntimeContainerBase
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.model.*
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOption
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.when.impl.AllOfConditional
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

/**
 * Transforms a given {@link ModelASTPipelineDef} into the {@link Root} used for actual runtime. Also attaches a
 * {@link ExecutionModelAction} to the run.
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class RuntimeASTTransformer {

	/**
	 * Enables or disables the script splitting behavior in {@Wrapper} which
	 * mitigates "Method code too large" and "Class too large" errors.
	 */
	@SuppressFBWarnings(value="MS_SHOULD_BE_FINAL", justification="For access from script console")
	public static boolean SCRIPT_SPLITTING_TRANSFORMATION = SystemProperties.getBoolean(
	RuntimeASTTransformer.class.getName() + ".SCRIPT_SPLITTING_TRANSFORMATION",
	false
	)

	/**
	 * Enables or disables allowing local variable declarations while script splitting.
	 * This severely reduces the effectiveness of script splitting.
	 */
	@SuppressFBWarnings(value="MS_SHOULD_BE_FINAL", justification="For access from script console")
	public static boolean SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES = SystemProperties.getBoolean(
	RuntimeASTTransformer.class.getName() + ".SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES",
	false
	)

	Wrapper wrapper = null

	RuntimeASTTransformer() {
	}

	/**
	 * Given a run, transform a {@link ModelASTPipelineDef}, attach the {@link ModelASTPipelineDef} to the
	 * run, and return an {@link ArgumentListExpression} containing a closure that returns the {@Root} we just created.
	 */
	@NonNull
	ArgumentListExpression transform(@NonNull SourceUnit sourceUnit, @NonNull ModelASTPipelineDef pipelineDef, @CheckForNull Run<?, ?> run) {
		wrapper = new Wrapper(sourceUnit, pipelineDef)
		Expression root = transformRoot(pipelineDef)
		if (run != null) {
			pipelineDef.removeSourceLocation()
			ExecutionModelAction action = run.getAction(ExecutionModelAction.class)
			if (action == null) {
				run.addAction(new ExecutionModelAction(pipelineDef))
			} else {
				action.addPipelineDef(pipelineDef)
				run.save()
			}
		}

		ClosureExpression result = wrapper.createPipelineClosureX(root)

		return args(result)
	}

	/**
	 * Generate the AST (to be CPS-transformed) for instantiating a {@link AbstractBuildConditionResponder}.
	 *
	 * @param original The parsed AST model.
	 * @param container The class of the container we're instantiating.
	 * @return The AST for a constructor call for this container class, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformBuildConditionsContainer(@CheckForNull ModelASTBuildConditionsContainer original,
			@NonNull Class container) {
		if (isGroovyAST(original) && !original.conditions.isEmpty()) {
			MapExpression nameToSteps = new MapExpression()
			original.conditions.each { cond ->
				Expression steps = transformStepsFromBuildCondition(cond)
				if (steps != null) {
					nameToSteps.addMapEntryExpression(constX(cond.condition), steps)
				}
			}
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(container), args(nameToSteps)))
		}
		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating an {@link Agent}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Agent}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformAgent(@CheckForNull ModelASTAgent original) {
		if (isGroovyAST(original) && original.agentType != null) {
			MapExpression m
			if (original.variables == null ||
			(original.variables instanceof ModelASTClosureMap &&
			((ModelASTClosureMap)original.variables).variables.isEmpty())) {
				// Zero-arg agent type
				MapExpression zeroArg = new MapExpression()
				zeroArg.addMapEntryExpression(constX(original.agentType.key), constX(true))
				m = zeroArg
			} else {
				BlockStatementMatch match =
						matchBlockStatement((Statement) original.sourceLocation)
				if (match != null) {
					m = recurseAndTransformMappedClosure(match.body)
				} else {
					throw new IllegalArgumentException("Expected a BlockStatement for agent but got an instance of ${original.sourceLocation.class}")
				}
			}
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Agent.class), args(wrapper.asWrappedScriptContextVariable(closureX(block(returnS(m)))))))
		}

		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating an {@link Environment}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Environment}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformEnvironment(@CheckForNull ModelASTEnvironment original) {
		if (isGroovyAST(original)) {
			return transformEnvironmentMap(original.variables)
		}
		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating an {@link Environment}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Environment}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformEnvironmentMap(@NonNull Map<ModelASTKey, ModelASTEnvironmentValue> variables, boolean disableWrapping = false) {
		if (!variables.isEmpty()) {
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Environment.class),
					args(
					generateEnvironmentResolver(variables, ModelASTValue.class, disableWrapping),
					generateEnvironmentResolver(variables, ModelASTInternalFunctionCall.class, disableWrapping)
					)))
		}
		return constX(null)
	}

	/**
	 * Create the AST for the {@link Environment.EnvironmentResolver} for this environment value type.
	 *
	 * @param original The parsed model of the environment
	 * @param valueType Either {@link ModelASTInternalFunctionCall} for credentials or {@link ModelASTValue} for env.
	 * @return The AST for instantiating the resolver.
	 */
	@NonNull
	private Expression generateEnvironmentResolver(@NonNull Map<ModelASTKey, ModelASTEnvironmentValue> variables, @NonNull Class valueType, boolean disableWrapping) {
		Set<String> keys = new HashSet<>()

		// We need to keep track of the environment keys for use in
		keys.addAll(variables.findAll { k, v -> v instanceof ModelASTValue }.collect { k, v -> k.key })

		MapExpression closureMap = new MapExpression()

		variables.each { k, v ->
			// Filter for only the desired value type - ModelASTValue for env vars, ModelASTInternalFunctionCall for
			// credentials.
			if (v instanceof ModelASTElement && valueType.isInstance(v) && v.sourceLocation != null) {
				if (v.sourceLocation instanceof Expression) {
					Expression toTransform = (Expression)v.sourceLocation

					if (valueType == ModelASTInternalFunctionCall && toTransform instanceof MethodCallExpression) {
						List<Expression> args = methodCallArgs((MethodCallExpression)toTransform)
						if (args.size() == 1) {
							toTransform = args.get(0)
						}
					}
					Expression expr = translateEnvironmentValue(k.key, toTransform, keys)
					if (expr != null) {
						if (!(expr instanceof ClosureExpression)) {
							expr = closureX(block(returnS(expr)))
						}

						// in the case of Matrix Cell Environment blocks, we know they don't need to be inside a closure
						// they are all literal key-values with no external references allowed.
						if (!disableWrapping) {
							expr = wrapper.asWrappedScriptContextVariable(expr)
						}

						closureMap.addMapEntryExpression(constX(k.key), expr)
					} else {
						throw new IllegalArgumentException("Empty closure for ${k.key}")
					}
				}
			}
		}

		Expression result = callX(ClassHelper.make(Environment.EnvironmentResolver.class), "instanceFromMap",
				args(closureMap))

		return result
	}

	/**
	 * Calls {@link #translateEnvironmentValue} and calls the result. This won't be evaluated until the parent closure
	 * itself is evaluated, as this is only called for children. May be null if the translation is null.
	 */
	@CheckForNull
	private Expression translateEnvironmentValueAndCall(String targetVar, Expression expr, Set<String> keys) {
		Expression translated = translateEnvironmentValue(targetVar, expr, keys)
		if (translated != null) {
			if (translated instanceof ClosureExpression) {
				return callX(translated, "call")
			} else {
				return translated
			}
		} else {
			return null
		}
	}

	/**
	 * Recursively translate any nested expressions within the given expression, setting any attempt to reference an
	 * environment variable we've defined to instead lazily call the closure defined in the resolver for that value.
	 */
	@CheckForNull
	private Expression translateEnvironmentValue(String targetVar, Expression expr, Set<String> keys) {
		Expression body = null
		if (expr instanceof ConstantExpression) {
			// If the expression is a constant, like 1, "foo", etc, just use that.
			return expr
		} else if (expr instanceof ClassExpression) {
			// If the expression is a class, just use that.
			return expr
		} else if (expr instanceof EmptyExpression) {
			// If it's an empty expression, just use that
			return expr
		} else if (expr instanceof BinaryExpression &&
		((BinaryExpression) expr).getOperation().getType() == Types.PLUS) {
			// If the expression is a binary expression of plusses, translate its components.
			BinaryExpression binExpr = (BinaryExpression) expr
			return plusX(
					translateEnvironmentValueAndCall(targetVar, binExpr.leftExpression, keys),
					translateEnvironmentValueAndCall(targetVar, binExpr.rightExpression, keys)
					)
		} else if (expr instanceof GStringExpression) {
			// If the expression is a GString, translate its values.
			GStringExpression gStrExpr = (GStringExpression) expr
			return new GStringExpression(gStrExpr.text,
					gStrExpr.strings,
					gStrExpr.values.collect {
						translateEnvironmentValueAndCall(targetVar, it, keys) }
					)
		} else if (expr instanceof PropertyExpression) {
			PropertyExpression propExpr = (PropertyExpression) expr
			if (propExpr.objectExpression instanceof VariableExpression &&
			((VariableExpression) propExpr.objectExpression).name == "env" &&
			keys.contains(propExpr.propertyAsString)) {
				if (propExpr.propertyAsString == targetVar) {
					// If this is the same variable we're setting, use getScriptPropOrParam, which will first try
					// script.getProperty(name), then script.getProperty('params').get(name).
					body = callX(
							varX("this"),
							constX("getScriptPropOrParam"),
							args(constX(propExpr.propertyAsString))
							)

				} else {
					// If the property this expression refers to is env.whatever, replace with the env getter.
					body = environmentValueGetterCall(propExpr.propertyAsString)
				}
			} else {
				// Otherwise, if the property is still on a variable, translate everything
				return propX(
						translateEnvironmentValueAndCall(targetVar, propExpr.objectExpression, keys),
						translateEnvironmentValueAndCall(targetVar, propExpr.property, keys)
						)
			}
		} else if (expr instanceof MethodCallExpression) {
			// If the expression is a method call, translate its arguments.
			MethodCallExpression mce = (MethodCallExpression) expr
			return callX(
					translateEnvironmentValueAndCall(targetVar, mce.objectExpression, keys),
					mce.method,
					args(mce.arguments.collect { a ->
						translateEnvironmentValueAndCall(targetVar, a, keys)
					})
					)
		} else if (expr instanceof VariableExpression) {
			VariableExpression ve = (VariableExpression) expr
			if (keys.contains(ve.name) && ve.name != targetVar) {
				// If the variable name is one we know is an environment variable, use the env getter, unless the reference
				// is to the same variable we're setting!
				body = environmentValueGetterCall(ve.name)
			} else if (ve.name == "this" || !(ve.accessedVariable instanceof DynamicVariable)) {
				// If the variable is this, or if this is a real variable, not a dynamic variable, just use it.
				return ve
			} else {
				// Otherwise, fall back to getScriptPropOrParam, which will first try script.getProperty(name), then
				// script.getProperty('params').get(name).
				body = callX(
						varX("this"),
						constX("getScriptPropOrParam"),
						args(constX(ve.name))
						)
			}
		} else if (expr instanceof ElvisOperatorExpression) {
			// If the expression is ?:, translate its components.
			ElvisOperatorExpression elvis = (ElvisOperatorExpression) expr
			return new ElvisOperatorExpression(
					translateEnvironmentValueAndCall(targetVar, elvis.trueExpression, keys),
					translateEnvironmentValueAndCall(targetVar, elvis.falseExpression, keys)
					)
		} else if (expr instanceof ClosureExpression) {
			// If the expression is a closure, translate its statements.
			ClosureExpression cl = (ClosureExpression) expr
			BlockStatement closureBlock = block()
			eachStatement(cl.code) { s ->
				closureBlock.addStatement(translateClosureStatement(targetVar, s, keys))
			}
			return closureX(
					cl.parameters,
					closureBlock
					)
		} else if (expr instanceof ArrayExpression) {
			// If the expression is an array, transform its contents.
			ArrayExpression a = (ArrayExpression) expr
			List<Expression> sizes = a.sizeExpression?.collect {
				translateEnvironmentValueAndCall(targetVar, it, keys)
			}
			List<Expression> expressions = a.expressions?.collect {
				translateEnvironmentValueAndCall(targetVar, it, keys)
			}

			return new ArrayExpression(a.elementType, expressions, sizes)
		} else if (expr instanceof ListExpression) {
			// If the expression is a list, transform its contents
			ListExpression l = (ListExpression) expr
			List<Expression> expressions = l.expressions?.collect {
				translateEnvironmentValueAndCall(targetVar, it, keys)
			}

			return new ListExpression(expressions)
		} else if (expr instanceof MapExpression) {
			// If the expression is a map, translate its entries.
			MapExpression m = (MapExpression) expr
			List<MapEntryExpression> entries = m.mapEntryExpressions?.collect {
				(MapEntryExpression)translateEnvironmentValueAndCall(targetVar, it, keys)
			}

			return new MapExpression(entries)
		} else if (expr instanceof MapEntryExpression) {
			// If the expression is a map entry, translate its key and value
			MapEntryExpression m = (MapEntryExpression) expr

			return new MapEntryExpression(translateEnvironmentValueAndCall(targetVar, m.keyExpression, keys),
					translateEnvironmentValueAndCall(targetVar, m.valueExpression, keys))
		} else if (expr instanceof BitwiseNegationExpression) {
			// Translate the nested expression - note, no test coverage due to bitwiseNegate not being whitelisted
			return new BitwiseNegationExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
		} else if (expr instanceof NotExpression) {
			// Translate the nested expression
			return new NotExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
		} else if (expr instanceof BooleanExpression) {
			// Translate the nested expression
			return new BooleanExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
		} else if (expr instanceof CastExpression) {
			// Translate the nested expression
			Expression transformed = translateEnvironmentValueAndCall(targetVar, expr.expression, keys)
			def cast = new CastExpression(expr.type, transformed, expr.ignoringAutoboxing)
			return cast
		} else if (expr instanceof ConstructorCallExpression) {
			// Translate the arguments
			return ctorX(expr.type, translateEnvironmentValueAndCall(targetVar, expr.arguments, keys))
		} else if (expr instanceof MethodPointerExpression) {
			// Translate the nested expression and method
			return new MethodPointerExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys),
					translateEnvironmentValueAndCall(targetVar, expr.methodName, keys))
		} else if (expr instanceof PostfixExpression) {
			// Translate the nested expression
			return new PostfixExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys), expr.operation)
		} else if (expr instanceof PrefixExpression) {
			// Translate the nested expression
			return new PrefixExpression(expr.operation, translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
		} else if (expr instanceof RangeExpression) {
			// Translate the from and to
			return new RangeExpression(translateEnvironmentValueAndCall(targetVar, expr.from, keys),
					translateEnvironmentValueAndCall(targetVar, expr.to, keys),
					expr.inclusive)
		} else if (expr instanceof TernaryExpression) {
			// Translate the true, false and boolean expressions
			TernaryExpression t = (TernaryExpression) expr
			return ternaryX(translateEnvironmentValueAndCall(targetVar, t.booleanExpression, keys),
					translateEnvironmentValueAndCall(targetVar, t.trueExpression, keys),
					translateEnvironmentValueAndCall(targetVar, t.falseExpression, keys))
		} else if (expr instanceof ArgumentListExpression) {
			// Translate the contents
			List<Expression> expressions = expr.expressions.collect {
				translateEnvironmentValueAndCall(targetVar, it, keys)
			}
			return args(expressions)
		} else if (expr instanceof TupleExpression) {
			// Translate the contents
			List<Expression> expressions = expr.expressions.collect {
				translateEnvironmentValueAndCall(targetVar, it, keys)
			}
			return new TupleExpression(expressions)
		} else if (expr instanceof UnaryMinusExpression) {
			// Translate the nested expression - unary ops are also not whitelisted and so aren't tested
			return new UnaryMinusExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
		} else if (expr instanceof UnaryPlusExpression) {
			// Translate the nested expression
			return new UnaryPlusExpression(translateEnvironmentValueAndCall(targetVar, expr.expression, keys))
		} else if (expr instanceof BinaryExpression) {
			// Translate the component expressions
			return new BinaryExpression(translateEnvironmentValueAndCall(targetVar, expr.leftExpression, keys),
					expr.operation,
					translateEnvironmentValueAndCall(targetVar, expr.rightExpression, keys))
		} else {
			throw new IllegalArgumentException("Got an unexpected " + expr.getClass() + " in environment, please report " +
			"at issues.jenkins-ci.org")
		}

		if (body != null) {
			return closureX(
					block(
					returnS(
					body
					)
					)
					)
		}

		return null
	}

	@CheckForNull
	private Statement translateClosureStatement(String targetVar, Statement s, Set<String> keys) {
		if (s == null) {
			return null
		} else if (s instanceof ExpressionStatement) {
			// Translate the nested expression
			return stmt(translateEnvironmentValueAndCall(targetVar, s.expression, keys))
		} else if (s instanceof ReturnStatement) {
			// Translate the nested expression
			return stmt(translateEnvironmentValueAndCall(targetVar, s.expression, keys))
		} else if (s instanceof IfStatement) {
			// Translate the boolean expression, the if block and the else block
			return ifElseS(translateEnvironmentValueAndCall(targetVar, s.booleanExpression, keys),
					translateClosureStatement(targetVar, s.ifBlock, keys),
					translateClosureStatement(targetVar, s.elseBlock, keys))
		} else if (s instanceof ForStatement) {
			// Translate the collection and loop block
			return new ForStatement(s.variable,
					translateEnvironmentValueAndCall(targetVar, s.collectionExpression, keys),
					translateClosureStatement(targetVar, s.loopBlock, keys))
		} else if (s instanceof WhileStatement) {
			// Translate the boolean expression's contents and the loop block
			BooleanExpression newBool = new BooleanExpression(translateEnvironmentValueAndCall(targetVar,
					s.booleanExpression?.expression, keys))
			return new WhileStatement(newBool, translateClosureStatement(targetVar, s.loopBlock, keys))
		} else if (s instanceof TryCatchStatement) {
			// Translate the try and finally statements, as well as any catch statements
			TryCatchStatement t = (TryCatchStatement) s
			TryCatchStatement newTry = new TryCatchStatement(translateClosureStatement(targetVar, t.tryStatement, keys),
					translateClosureStatement(targetVar, t.finallyStatement, keys))
			t.catchStatements.each { c ->
				newTry.addCatch((CatchStatement)translateClosureStatement(targetVar, c, keys))
			}
			return newTry
		} else if (s instanceof CatchStatement) {
			// Translate the nested statement
			return catchS(s.variable, translateClosureStatement(targetVar, s.code, keys))
		} else {
			throw new IllegalArgumentException("Got an unexpected " + s.getClass() + " in environment, please " +
			"report at issues.jenkins-ci.org")
		}
	}

	/**
	 * Generates the method call for fetching the closure for a given environment key and calling it.
	 */
	@NonNull
	private MethodCallExpression environmentValueGetterCall(String name) {
		return callX(callThisX("getClosure", constX(name)), "call")
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Libraries}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Libraries}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformLibraries(@CheckForNull ModelASTLibraries original) {
		if (isGroovyAST(original) && !original.libs.isEmpty()) {
			ListExpression listArg = new ListExpression()
			original.libs.each { l ->
				if (l.sourceLocation instanceof Expression) {
					listArg.addExpression(wrapper.asWrappedScriptContextVariable((Expression)l.sourceLocation))
				}
			}
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Libraries.class), args(listArg)))
		}

		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Options}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Options}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformOptions(@CheckForNull ModelASTOptions original) {
		if (isGroovyAST(original) && !original.options.isEmpty()) {
			List<ModelASTOption> jobProps = new ArrayList<>()
			List<ModelASTOption> options = new ArrayList<>()
			List<ModelASTOption> wrappers = new ArrayList<>()

			SymbolLookup symbolLookup = SymbolLookup.get()

			original.options.each { o ->
				if (!original.inStage && symbolLookup.findDescriptor(JobProperty.class, o.name) != null) {
					jobProps.add(o)
				} else if (symbolLookup.findDescriptor(DeclarativeOption.class, o.name) != null) {
					options.add(o)
				} else if (StepDescriptor.byFunctionName(o.name) != null) {
					wrappers.add(o)
				}
			}
			MapExpression optsMap = new MapExpression()
			MapExpression wrappersMap = new MapExpression()

			options.each { o ->
				if (o.getSourceLocation() instanceof Statement) {
					MethodCallExpression expr = matchMethodCall((Statement) o.getSourceLocation())
					if (expr != null) {
						optsMap.addMapEntryExpression(constX(o.name), methodCallToDescribable(expr, DeclarativeOption.class))
					}
				}
			}
			wrappers.each { w ->
				if (w.getSourceLocation() instanceof Statement) {
					MethodCallExpression expr = matchMethodCall((Statement) w.getSourceLocation())
					if (expr != null) {
						List<Expression> methArgs = methodCallArgs(expr)
						if (methArgs.size() == 1) {
							wrappersMap.addMapEntryExpression(constX(w.name), methArgs.get(0))
						} else if (methArgs.size() > 1) {
							ListExpression argList = new ListExpression(methArgs)
							wrappersMap.addMapEntryExpression(constX(w.name), argList)
						} else {
							wrappersMap.addMapEntryExpression(constX(w.name), constX(null))
						}
					}
				}
			}

			Expression result
			if (original.inStage) {
				result = ctorX(ClassHelper.make(StageOptions.class), args(optsMap, wrappersMap))
			} else {
				result = ctorX(ClassHelper.make(Options.class),
						args(transformListOfDescribables(jobProps, JobProperty.class), optsMap, wrappersMap))
			}

			// This is not a large element but it may have script references,
			// safe and simple to wrap in a context variable
			return wrapper.asWrappedScriptContextVariable(result)
		}

		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Parameters}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Parameters}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformParameters(@CheckForNull ModelASTBuildParameters original) {
		return wrapper.asWrappedScriptContextVariable(transformDescribableContainer(original, original?.parameters, Parameters.class, ParameterDefinition.class))
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link PostBuild}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link PostBuild}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformPostBuild(@CheckForNull ModelASTPostBuild original) {
		return transformBuildConditionsContainer(original, PostBuild.class)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link PostStage}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link PostStage}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformPostStage(@CheckForNull ModelASTPostStage original) {
		return transformBuildConditionsContainer(original, PostStage.class)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Root}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Root}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformRoot(@CheckForNull ModelASTPipelineDef original) {
		if (isGroovyAST(original)) {
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Root.class),
					args(transformAgent(original.agent),
					transformStages(original.stages),
					transformPostBuild(original.postBuild),
					transformEnvironment(original.environment),
					transformTools(original.tools),
					transformOptions(original.options),
					transformTriggers(original.triggers),
					transformParameters(original.parameters),
					transformLibraries(original.libraries),
					constX(original.stages.getUuid().toString()))))
		}

		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Stage}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Stage}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformStage(@CheckForNull ModelASTStage original) {
		if (isGroovyAST(original)) {
			// Matrix is a special form of parallel
			// At runtime, they behave the same
			Expression parallel = original.parallel != null ?
					transformStages(original.parallel) :
					transformMatrix(original.matrix)

			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Stage.class),
					args(constX(original.name),
					transformStepsFromStage(original),
					transformAgent(original.agent),
					transformPostStage(original.post),
					transformStageConditionals(original.when, original.name, original),
					transformTools(original.tools),
					transformEnvironment(original.environment),
					constX(original.failFast != null ? original.failFast : false),
					transformOptions(original.options),
					transformStageInput(original.input, original.name),
					transformStages(original.stages),
					parallel,
					constX(null))))
		}

		return constX(null)
	}

	@NonNull
	Expression transformStageInput(@CheckForNull ModelASTStageInput original, String stageName) {
		if (isGroovyAST(original)) {
			Expression paramsExpr = constX(null)
			if (!original.parameters.isEmpty()) {
				paramsExpr = wrapper.asWrappedScriptContextVariable(transformListOfDescribables(original.parameters, ParameterDefinition.class))
			}
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(StageInput.class),
					args(valueOrNull(original.message),
					valueOrNull(original.id, stageName),
					valueOrNull(original.ok),
					valueOrNull(original.submitter),
					valueOrNull(original.submitterParameter),
					paramsExpr)))
		}
		return constX(null)
	}

	@SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD")
	private Expression valueOrNull(@CheckForNull ModelASTValue value, Object defaultValue = null) {
		if (value?.sourceLocation instanceof Expression) {
			return wrapper.asWrappedScriptContextVariable((Expression) value.sourceLocation)
		} else {
			return constX(defaultValue)
		}
	}

	/**
	 * Instantiates a stage's when variable. Note that this does not instantiate the when conditions inside, it just
	 * creates a closure that will return them when needed. This is to ensure lazy evaluation of variables.
	 *
	 * @param original
	 * @return
	 */
	@NonNull
	Expression transformStageConditionals(@CheckForNull ModelASTWhen original, String stageName, ModelASTStageBase stage) {
		ModelASTWhen when = handleInvisibleWhenConditions(original, stageName, stage)

		// Handle either cases of Groovy AST transformation or auto-generated InvisibleWhen containers.
		if ((isGroovyAST(when) || when instanceof InvisibleWhen) && !when.getConditions().isEmpty()) {
			ListExpression closList = new ListExpression()
			when.getConditions().each { cond ->
				if (cond.name != null) {
					DeclarativeStageConditionalDescriptor desc =
							(DeclarativeStageConditionalDescriptor) SymbolLookup.get().findDescriptor(
							DeclarativeStageConditional.class, cond.name)
					if (desc != null) {
						closList.addExpression(desc.transformToRuntimeAST(cond))
					}
				}
			}

			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(StageConditionals.class),
					args(wrapper.asScriptContextVariable(closureX(block(returnS(closList)))),
					constX(when.beforeAgent != null ? when.beforeAgent : false),
					constX(when.beforeInput != null ? when.beforeInput : false),
					constX(when.beforeOptions != null ? when.beforeOptions : false)
					)))
		}
		return constX(null)
	}

	/**
	 * Add any invisible global when conditions to an auto-generated {@link InvisibleWhen}. The new invisible conditions
	 * will need to be satisfied as well as any existing conditions. If there are existing conditions, the new container
	 * will delegate everything other than condition listing to the original container.
	 *
	 * @param when The original, possibly null, when container.
	 * @return The new when container with any invisible conditions added.
	 */
	@CheckForNull
	final ModelASTWhen handleInvisibleWhenConditions(@CheckForNull ModelASTWhen when, String stageName, ModelASTStageBase stage) {
		List<ModelASTWhenContent> invisibles = DeclarativeStageConditionalDescriptor.allInvisible()
				.findAll { it != null }
				.collect { whenConditionForDescriptor(it, stageName, stage) }

		if (invisibles.size() == 0) {
			return when
		}
		ModelASTWhen newWhen = new InvisibleWhen()
		List<ModelASTWhenContent> newConditions = new ArrayList<>(invisibles)

		if (when != null) {
			List<ModelASTWhenContent> originalConditions = when.getConditions()
			newConditions.addAll(originalConditions)
			newWhen.setOriginalWhen(when)
		}
		ModelASTWhenCondition newParent = new InvisibleGlobalWhenCondition()
		newParent.setName(SymbolLookup.getSymbolValue(AllOfConditional.class).first())
		newParent.setChildren(newConditions)

		newWhen.setConditions(Collections.singletonList(newParent))
		return newWhen
	}

	@CheckForNull
	private ModelASTWhenContent whenConditionForDescriptor(@NonNull DeclarativeStageConditionalDescriptor d, String stageName, ModelASTStageBase stage) {
		Set<String> symbols = SymbolLookup.getSymbolValue(d)
		if (symbols.isEmpty()) {
			return null
		}
		ModelASTWhenCondition condition = new InvisibleGlobalWhenCondition(stageName, stage)
		condition.setName(symbols.first())
		return condition
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Stages}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Stages}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformStages(@CheckForNull ModelASTStages original) {
		if (isGroovyAST(original) && !original.stages.isEmpty()) {
			ArrayList<Expression> argList = new ArrayList<>()
			original.stages.each { s ->
				argList.add(transformStage(s))
			}

			ClassNode classNode = original instanceof ModelASTParallel ?
					ClassHelper.make(Parallel.class) :
					ClassHelper.make(Stages.class)

			return wrapper.asExternalMethodCall(ctorX(classNode, args(wrapper.asExternalMethodCall(argList))))
		}

		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Matrix}.
	 * Generates a synthetic stage for each combination of axes and excludes.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Matrix}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformMatrix(@CheckForNull ModelASTMatrix original) {
		if (isGroovyAST(original) && !original?.stages?.stages?.isEmpty() && !original?.axes?.axes?.isEmpty()) {

			// generate matrix combinations of axes - cartesianProduct
			Set<Map<ModelASTKey, ModelASTValue>> expansion = expandAxes(original.axes.axes)

			// remove excluded combinations
			filterExcludes(expansion, original.excludes)

			Expression stagesExpression = transformStages(original.stages)

			// With script splitting stagesExpression is a method that creates a new stages expression at runtime
			// If disabled, we still want to make this a script closure call so we can reuse it instead of generating code for every cell
			if (!SCRIPT_SPLITTING_TRANSFORMATION) {
				stagesExpression = wrapper.asWrappedScriptContextVariable(stagesExpression, true)
			}

			ArrayList<Expression> argList = new ArrayList<>()
			expansion.each { item ->
				argList.add(transformMatrixStage(item, original, stagesExpression))
			}

			// return matrix class containing the list of generated stages
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Matrix.class), args(wrapper.asExternalMethodCall(argList))))
		}

		return constX(null)
	}

	@NonNull
	private Set<Map<ModelASTKey, ModelASTValue>> expandAxes(@NonNull List<ModelASTAxis> axes) {
		Set<Map<ModelASTKey, ModelASTValue>> result = new LinkedHashSet<>()
		// using LinkedHashMap to maintain insertion order
		// axes will be added in the order they are declared
		result.add(new LinkedHashMap<ModelASTKey, ModelASTValue>())
		axes.each { axis ->
			def interim = result
			result = new LinkedHashSet<Map<ModelASTKey, ModelASTValue>>()
			axis.values.each { value ->
				interim.each {
					Map<ModelASTKey, ModelASTValue> generated = it.clone()
					generated.put(axis.name, value)
					result.add(generated)
				}
			}
		}
		return result
	}

	@NonNull
	private void filterExcludes(@NonNull Set<Map<ModelASTKey, ModelASTValue>> expansion, @NonNull ModelASTExcludes excludes) {
		if (isGroovyAST(excludes)) {
			excludes.excludes.each { exclude ->
				Set<Map<ModelASTKey, ModelASTValue>> filter = expansion.clone()
				exclude.getExcludeAxes().each { excludeAxis ->
					filter.removeAll { !excludeAxis.inverse ^ excludeAxis.values.contains(it.get(excludeAxis.name)) }
				}
				expansion.removeAll(filter)
			}
		}
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Stage}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Stage}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformMatrixStage(@CheckForNull Map<ModelASTKey, ModelASTValue> cell, @NonNull ModelASTMatrix original, @CheckForNull Expression stagesExpression) {
		if (isGroovyAST(original)) {

			//     create a generated stage with unique name based on combination
			//     TODO: maybe if there is only one stage, use it as the template for these synthetic stages?  Avoid two layers of stages when one would do.

			List<String> cellLabels = new ArrayList<>();
			cell.each { cellLabels.add(it.key.key.toString() + " = '" + it.value.value.toString() + "'") }

			// TODO: Do I need to create a new ModelASTStage each time?  I don't think so.
			String name = "Matrix - " + cellLabels.join(", ")

			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Stage.class),
					args(constX(name),
					constX(null), // steps
					transformAgent(original.agent),
					transformPostStage(original.post),
					transformStageConditionals(original.when, name, original),
					transformTools(original.tools),
					transformEnvironment(original.environment),
					constX(false), // failfast on serial is not interesting
					transformOptions(original.options),
					transformStageInput(original.input, name),
					stagesExpression,
					constX(null), // parallel
					transformEnvironmentMap(cell, true))))
			//  matrixCellEnvironment holding values for this cell in the matrix
		}

		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link StepsBlock} for a {@link Stage}.
	 *
	 * @param original The parsed AST model
	 * @return A method call of {@link Utils#createStepsBlock(Closure)}, or a constant null expression if the original
	 * is null.
	 */
	@NonNull
	Expression transformStepsFromStage(@CheckForNull ModelASTStage original) {
		if (isGroovyAST(original)) {
			BlockStatementMatch stageMatch = matchBlockStatement((Statement) original.sourceLocation)
			if (stageMatch != null) {
				Statement stepsMethod = asBlock(stageMatch.body.code).statements.find { s ->
					matchMethodCall(s)?.methodAsString == "steps"
				}
				if (stepsMethod != null) {
					BlockStatementMatch stepsMatch = matchBlockStatement(stepsMethod)
					if (stepsMatch != null) {
						Expression transformedBody = StepRuntimeTransformerContributor.transformStage(original, stepsMatch.body)
						transformedBody = wrapper.asScriptContextVariable(transformedBody)
						return callX(ClassHelper.make(Utils.class), "createStepsBlock",
								args(transformedBody))
					}
				}
			}
		}

		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link StepsBlock} for a post condition.
	 *
	 * @param original The parsed AST model
	 * @return A method call of {@link Utils#createStepsBlock(Closure)}, or a constant null expression if the original
	 * is null.
	 */
	@NonNull
	Expression transformStepsFromBuildCondition(@CheckForNull ModelASTBuildCondition original) {
		if (isGroovyAST(original)) {
			BlockStatementMatch condMatch = matchBlockStatement((Statement) original.sourceLocation)
			Expression transformedBody = StepRuntimeTransformerContributor.transformBuildCondition(original, condMatch.body)
			transformedBody = wrapper.asScriptContextVariable(transformedBody)
			return callX(ClassHelper.make(Utils.class), "createStepsBlock",
					args(transformedBody))
		}
		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Tools}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Tools}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformTools(@CheckForNull ModelASTTools original) {
		if (isGroovyAST(original) && !original.tools?.isEmpty()) {
			MapExpression toolsMap = new MapExpression()
			original.tools.each { k, v ->
				if (v.sourceLocation != null && v.sourceLocation instanceof Expression) {
					Expression expr = (Expression)v.sourceLocation
					if (!(expr instanceof ClosureExpression)) {
						expr = closureX(block(returnS(expr)))
					}
					toolsMap.addMapEntryExpression(constX(k.key), wrapper.asScriptContextVariable(expr))

				}
			}
			return wrapper.asExternalMethodCall(ctorX(ClassHelper.make(Tools.class), args(toolsMap)))
		}
		return constX(null)
	}

	/**
	 * Generates the AST (to be CPS-transformed) for instantiating {@link Triggers}.
	 *
	 * @param original The parsed AST model
	 * @return The AST for a constructor call for {@link Triggers}, or the constant null expression if the original
	 * cannot be transformed.
	 */
	@NonNull
	Expression transformTriggers(@CheckForNull ModelASTTriggers original) {
		return wrapper.asWrappedScriptContextVariable(transformDescribableContainer(original, original?.triggers, Triggers.class, Trigger.class))
	}

	/**
	 * In order to reduce the occurrence of "Method code too large" and "Class too large" this class can
	 * attempt to split the generated code into multiple classes and methods.
	 *
	 * * "Method code too large" error is thrown when a single method exceeds the 64k byte-code per method limit.
	 *      This is generally caused by script initialization method which by default holds _all_ of the code in the script.
	 *      Mitigated by moving code into separate functions. Can even be functions that return closures that run the
	 *      same behaviors as before.
	 * * "Class too large" error is thrown when there are more that 64k constants in a single class.
	 *      A range of things can cause this but a large number of strings and closures, when
	 *      combined with CPS processing seems to exacerbate the issue.
	 *      Mitigated by moving code in to separate classes.  The classes are special passthrough containers which extend
	 *      {@link RuntimeContainerBase}.
	 *
	 * Neither of these can be prevented completely but they happen signifcantly less often.
	 *
	 * That said there are a number of limitations to where code can safely be moved. With deep analysis of all closures
	 * the script could be transformed in any number of ways. This code splitting functionality works mostly without deep
	 * knowledge of the closures provided by the user, instead relying mostly on the Declarative structure to guide it.
	 *
	 * This script uses three distinct strategies when to decide where to move code:
	 *
	 * 1. All code generated from sections and directives as part of the Declarative runtime is moved into methods on
	 *      automatically generated container classes. This is used for basically all the calls to classes in
	 *      org.jenkinsci.plugins.pipeline.modeldefinition.model -
	 *      {@link org.jenkinsci.plugins.pipeline.modeldefinition.model.Root} and so on.
	 *      These containers hold no binding state of their own, they only passthrough all their calls to their parent
	 *      WorkflowScript instance.  An instance of each of these classes is assigned to a variable in the parent script.
	 *      Code is moved into a method one of these classes and the code that was in the parent script is replaced
	 *      by a call to that method.  This moves a good amount of code and constants out of the parent script.
	 *
	 * 2. Expressions provided by the user (closures and values) are wrapped in generated closures that return
	 *      the user-provided expression when they are called. The wrapper closures are assigned to variables added to
	 *      the binding context of the WorkflowScript.  Wrapper closures can then safely be called from the methods of
	 *      the container classes generated in #1. Code inside the wrapper is considered to have been declared
	 *      inside the script. This enables #1 while guaranteeing that from the user perspective the transformed code
	 *      behaves the same as the original code.
	 *
	 * 3. The variable declarations from 1 and 2 are grouped into methods whose definitions are added to the script,
	 *      and which are then called at the start of the script exectution.
	 *      This results in these variables being added to the binding of the script, accessible at runtime.
	 *      This last step keeps the script initialization method from getting too large.
	 *      NOTE: Using methods here subtly changes the behavior of the script in a way that will break user provided
	 *      code from #2 one specific scenario (see {@link #declareClosureScopedHandles for details).
	 *      When Wrapper detects that scenario, it falls back to grouping the variables
	 *      declarations into closures instead of methods.
	 *      This is less effective, but still an improvement.
	 */
	static final class Wrapper {
		private final SourceUnit sourceUnit
		private final ModuleNode moduleNode
		private final Set<String> nameSet = new HashSet<>()
		private final List<Statement> pipelineElementHandles = new ArrayList<>()
		private final Map<String, ClassNode> methodClassNode = new HashMap<>()
		private final Long pipelineId

		// Group size is somewhat small.  Having more smaller classes is better than accidentally make a class too large
		private final int groupSize = 50

		// Declaration grouping is a little larger.
		// We don't want to end up with method too large errors, but we also can't have more than around 250 statements
		// in the script initialization method.
		private final int declarationGroupSize = 100


		private Wrapper(@NonNull SourceUnit sourceUnit, @NonNull ModelASTPipelineDef pipelineDef) {
			this.sourceUnit = sourceUnit
			this.moduleNode = sourceUnit.AST
			pipelineId = pipelineDef.toGroovy().hashCode().toLong()
		}

		/**
		 * Create a unique name for an item
		 * @param groupName the name of the grouping of items mostly helps with debugging
		 * @return a unique name that won't conflict with any other in the script
		 */
		@NonNull
		private String createStableUniqueName(@NonNull String groupName) {
			long id = Math.abs(Objects.hash(groupName, nameSet.size(), pipelineId).toLong())
			String name = "__model__${groupName}_${nameSet.size()}_${id}__"
			// This method assumes single threaded generation.
			// If the transform is multi-threaded, the naming would be non-deterministic.
			if (!nameSet.add(name)) {
				throw new RuntimeException("Name collision during runtime model generation. When did model generation become multi-threaded?")
			}
			return name
		}

		/**
		 * Creates the Pipeline closure including populating it with element handle declarations
		 * @param root the Root element generated by processing the pipeline ast
		 * @return a closure to be passed as an arg to the Pipeline runtime
		 */
		@NonNull
		ClosureExpression createPipelineClosureX(@NonNull Expression root) {

			BlockStatement pipelineBlock = block()

			declareClosureScopedHandles(pipelineBlock)

			declareFunctionGroupedHandles(pipelineBlock)

			// finally return the pipeline runtime elements
			pipelineBlock.addStatement(returnS(root))

			return closureX(pipelineBlock)

		}


		/**
		 * Adds handles to the Pipeline block directly, if needed.
		 *
		 * Adding all handle declarations directly to the Pipeline block/closure will error for 250 or more handles.
		 * It will also often encounter "method code too large" errors long before that.
		 *
		 * Moving groups of handle declarations to script-level methods still adds those handles to the script binding.
		 * and avoids "method code too large" errors.
		 *
		 * Using methods here subtly changes the behavior of the script in a way that will break user provided
		 * code in specific scenarios (see below). When this method detects one of those scenarios,
		 * it falls back to grouping the handle declarations into closures instead of methods.
		 *
		 * Problem scenarios:
		 *
		 * a. Handles declared inside methods cannot access a script's local variables ("def someVar = 0"). T
		 *      his is a limitation of Groovy itself.
		 *
		 * b. Methods declared in the script cannot be invoked from outside the script, because "invokeMethod()" is not
		 * whitelisted by the groovy sandbox.
		 *
		 * The sandbox limitation is already address by keeping any user provided code in closures declared inside the script.
		 * Any external container classes always call back into one of these closures which are able to call local methods.
		 *
		 * The script-local "def" variables are another matter.
		 *
		 *      def localVar = "script-local variable"
		 *      boundVar = "binding variable"
		 *      def localFunction() {
		 *          // Succeeds
		 *          println boundVar
		 *
		 *          // Fails
		 *          println localVar
		 *      }
		 *
		 * Variables like "boundVar" are held in the script's binding. Script-local "def" variables ("localVar" above)
		 * are defined on the WorkflowScript class itself and can only be access from inside the script or from closures defined
		 * in the initialization function of the script. They cannot be accessed from other functions or classes,
		 * nor from closures defined in other functions or classes.  Thus, when the wrapper puts the closure declarations
		 * into functions they can no longer access script-local "def" variables.
		 *
		 * To maintain some support for local variables, this method detects the presence of script-local "def" variables
		 * and adds handles to closures instead of methods.
		 *
		 * Currently, it only checks if "pipeline {}" is not the only top level element in script, and in that case it
		 * add _all_ handles to closures.
		 *
		 * This solution is sufficient for now, but a better solution would be to:
		 *
		 * * Specifically detect that local variables are used
		 * * Log a warning that this is not advised
		 * * detect which handles reference local variables and then only declare those handles in closures.
		 *
		 * @param pipelineBlock the block statement to add declarations to
		 */
		@NonNull
		private void declareClosureScopedHandles(@NonNull BlockStatement pipelineBlock) {
			def closureScopedHandles = prepareClosureScopedHandles()
			if (closureScopedHandles.size() == 0) {
				return
			}

			BlockStatement currentBlock = block()
			int count = 0
			closureScopedHandles.each { item ->
				if (count++ >= declarationGroupSize) {
					count = 1
					pipelineBlock.addStatement(stmt(callX(closureX(currentBlock), 'call')))
					currentBlock = block()
				}

				currentBlock.addStatement(item)
			}
			// These variable handles are declared in functions, but will still be bound to the script context
			// Doing this here ensures the variables make the trip across to run time - if they don't make it, neither did this pipeline
			pipelineBlock.addStatement(stmt(callX(closureX(currentBlock), 'call')))
		}

		@NonNull
		private List<Statement> prepareClosureScopedHandles(@NonNull BlockStatement pipelineBlock) {
			ArrayList<Statement> result = new ArrayList<Statement>()
			ArrayList<DeclarationExpression> declarations = new ArrayList<DeclarationExpression>()
			moduleNode.statementBlock.statements.each { item ->
				if (item instanceof ExpressionStatement) {
					ExpressionStatement es = (ExpressionStatement) item
					if (es.expression instanceof DeclarationExpression) {
						declarations.add((DeclarationExpression) es.expression)
					}
				}
			}

			// if we're not doing script splitting, keep the old behavior that preserves declared variable functionality in matrix
			if (!declarations.isEmpty()) {
				result.addAll(pipelineElementHandles)
				pipelineElementHandles.clear()

				if (SCRIPT_SPLITTING_TRANSFORMATION && !SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES) {
					def declarationNames = []
					declarations.each { item ->
						def left = item.getLeftExpression()
						if (left instanceof VariableExpression) {
							declarationNames.add(((VariableExpression) left).getName())
						} else if (left instanceof ArgumentListExpression) {
							left.each { arg ->
								if (arg instanceof VariableExpression) {
									declarationNames.add(((VariableExpression) arg).getName())
								} else {
									declarationNames.add("Unrecognized expression: " + arg.toString())
								}
							}
						} else {
							declarationNames.add("Unrecognized declaration structure: " + left.toString())
						}
					}
					throw new IllegalStateException("[JENKINS-34987] SCRIPT_SPLITTING_TRANSFORMATION is an experimental feature of Declarative Pipeline and is incompatible with local variable declarations inside a Jenkinsfile. " +
					"As a temporary workaround, you can add the '@Field' annotation to these local variable declarations. " +
					"However, use of Groovy variables in Declarative pipeline, with or without the '@Field' annotation, is not recommended or supported. " +
					"To use less effective script splitting which allows local variable declarations without changing your pipeline code, set SCRIPT_SPLITTING_ALLOW_LOCAL_VARIABLES=true . " +
					"Local variable declarations found: " + declarationNames.sort().join(", ") + ". ")
				}
			}


			// In a future version, it may be possible to detect closures that reference script-local variables
			// and then only use closure scoped handles for those closures.
			return result
		}

		/**
		 * Adds groups of handle declarations to functions and adds calls to those functions to the pipeline block.
		 * Avoid "method code too large" errors and other compiler breaks related to Groovy, JVM, and CPS limitations.
		 * @param pipelineBlock
		 */
		@NonNull
		private void declareFunctionGroupedHandles(@NonNull BlockStatement pipelineBlock) {
			if (pipelineElementHandles.size() == 0) {
				return
			}

			BlockStatement currentBlock = block()
			int count = 0
			pipelineElementHandles.each { item ->
				if (count++ >= declarationGroupSize) {
					count = 1
					pipelineBlock.addStatement(stmt(defineMethodAndCall("Declaration", ClassHelper.VOID_TYPE, currentBlock)))
					currentBlock = block()
				}

				currentBlock.addStatement(item)
			}

			// These variable handles are declared in functions, but will still be bound to the script context
			// Doing this here ensures the variables make the trip across to run time - if they don't make it, neither did this pipeline
			pipelineBlock.addStatement(stmt(defineMethodAndCall("Declaration", ClassHelper.VOID_TYPE, currentBlock)))

			pipelineElementHandles.clear()
		}

		/**
		 * Turns a constructor call into a function call that returns the constructed instance.
		 * The function is declared on a separate class, outside of the script context.
		 * The function cannot reliably access script bindings except via handles to script context variables.
		 *
		 * @param expression the constructor call to wrapped
		 * @return call to a function that returns an instance of type
		 */
		@NonNull
		Expression asExternalMethodCall(@NonNull ConstructorCallExpression expression) {
			if (!SCRIPT_SPLITTING_TRANSFORMATION) {
				return expression
			}

			return asExternalMethodCall(expression.type.nameWithoutPackage, expression.type, expression)
		}

		/**
		 * Turns a list of expressions into a closure that returns a list instance (not a ListExpression).
		 * The closure is defined outside the script context and cannot reliably access script bindings except via handles.
		 *
		 * Works around limitations on ListExpression literal declaration
		 * (literal "[item0, item1, ...]") being limited to 250 items.
		 *
		 * @param expressions the list of expressions to be wrapped in a closure call
		 * @return call to a closure that returns the provided ListExpression
		 */
		@NonNull
		Expression asExternalMethodCall(@NonNull List<Expression> listExpression) {
			if (!SCRIPT_SPLITTING_TRANSFORMATION) {
				return new ListExpression(listExpression)
			}

			return toExternalListMethod(listExpression)
		}

		/**
		 * Turns an expression into a script bound variable declaration returned from a closure.
		 * This delays the evaluation of that contents of these variables to when the closure is called,
		 * and also evaluates the contents in the context of the current script environment.
		 *
		 * @param expression the expression to be replaced with a closure wrapped variable
		 * @param force ignore SCRIPT_SPLITTING_TRANSFORMATION and force the expression to be wrapped
		 * @return call to a closure that returns the provided expression
		 */
		@NonNull
		Expression asWrappedScriptContextVariable(@NonNull Expression expression, boolean force = false) {
			if (!SCRIPT_SPLITTING_TRANSFORMATION && !force) {
				return expression
			}

			Expression variable = createScriptContextVariable(closureX(block(returnS(expression))), createStableUniqueName('Closure'))
			return callX(variable, 'call')
		}

		/**
		 * Turns an expression into a script bound variable declaration. This does not wrap in closure.
		 * Evaluation will occur at the start of the pipeline run.
		 *
		 * @param expression the expression to be replaced with a variable
		 * @return a variable expression that evaluates to expression
		 */
		@NonNull
		Expression asScriptContextVariable(@NonNull Expression expression) {
			if (!SCRIPT_SPLITTING_TRANSFORMATION) {
				return expression
			}

			return createScriptContextVariable(expression, createStableUniqueName('Variable'))
		}

		/**
		 * Turns an expression into a script bound variable declaration. This does not wrap in closure.
		 * Evaluation will occur at the start of the pipeline run.
		 *
		 * NOTE: This private because the name is unchecked and could collide with other names.
		 * It also does not check SCRIPT_SPLITTING_TRANSFORMATION, it depends on callers to do that.
		 * Use with caution.
		 *
		 * @param expression the expression to be replaced with a variable
		 * @param name specific name to use for this variable.
		 * @return a variable expression that evaluates to expression
		 */
		@NonNull
		private Expression createScriptContextVariable(@NonNull Expression expression, @NonNull String name) {
			VariableExpression variable = varX(name)

			Expression declarationExpression = new BinaryExpression(variable, ASSIGN, expression)
			pipelineElementHandles.add(stmt(declarationExpression))

			variable = varX(new DynamicVariable(name, false))

			return variable
		}

		/**
		 * Turns an expression into a script bound variable declaration returned from a closure.
		 * This delays the evaluation of that contents of these variables to when the closure is called,
		 * and also evaluates the contents in the context of the current script environment.
		 *
		 * NOTE:
		 * This method does not check SCRIPT_SPLITTING_TRANSFORMATION.
		 *
		 * @param expression the expression to be replaced with a variable
		 * @return call to a function that returns the provided expression
		 */
		@NonNull
		private Expression defineMethodAndCall(@NonNull String groupName, @NonNull ClassNode returnType, @NonNull Statement statement) {

			String name = createStableUniqueName(groupName)

			// The instance method referenced via script binding
			MethodNode method = new MethodNode(name, ACC_PUBLIC, returnType, [] as Parameter[], [] as ClassNode[], statement)
			moduleNode.getScriptClassDummy().addMethod(method)

			return callThisX(name)
		}

		/**
		 * Returns a call expression that will return the passed in expression.
		 * This allows us to split the pipeline ast into small enough chunks to avoid JVM class and method size limits
		 * @param groupName Name of the grouping to add this function to
		 * @param returnType return type of the function
		 * @param expression expression to be returned
		 * @return callX that returns the value of returnXBody
		 */
		@NonNull
		Expression asExternalMethodCall(@NonNull String groupName, @NonNull ClassNode returnType, @NonNull Expression expression) {
			if (!SCRIPT_SPLITTING_TRANSFORMATION) {
				return expression
			}

			// The only thing these methods need to do is contain something to return
			return asExternalMethodCall(groupName, returnType, block(returnS(expression)))
		}
		/**
		 * Returns a call expression that will return the passed in expression.
		 * This allows us to split the pipeline ast into small enough chunks to avoid JVM class and method size limits
		 * @param groupName Name of the grouping to add this function to
		 * @param returnType return type of the function
		 * @param expression expression to be returned
		 * @return callX that returns the value of returnXBody
		 */
		@NonNull
		Expression asExternalMethodCall(@NonNull String groupName, @NonNull ClassNode returnType, @NonNull Statement methodBody) {
			if (!SCRIPT_SPLITTING_TRANSFORMATION) {
				throw new RuntimeException("This function cannot be disabled. Pass through of original expression is not possible.")
			}

			// We break the the ast graph into classes with static mathods to work around JVM class and method size limitations
			// However, class loading isn't free, so we also don't want a single method per class
			ClassNode classNode = methodClassNode[groupName]
			// If we don't have a classNode for this group name or if this class has reached groupSize, start a new class
			if (classNode == null || classNode.methods.size() >= groupSize) {
				// Get an uncreative unique class name
				String className = createStableUniqueName(groupName)

				classNode = new ClassNode("generated." + className, ACC_PUBLIC, ClassHelper.make(RuntimeContainerBase.class))
				classNode.addConstructor(ACC_PUBLIC, [
					new Parameter(ClassHelper.make(CpsScript.class), "workflowScript")] as Parameter[], [
					ClassHelper.make(IOException.class)] as ClassNode[],
				block(ctorSuperS(varX("workflowScript"))))

				this.moduleNode.addClass(classNode)

				// Add an instance of this class to the variables that can be referenced from anywhere in this script
				createScriptContextVariable(ctorX(classNode, args(varX('this'))), className)

				methodClassNode[groupName] = classNode
			}

			String className = classNode.nameWithoutPackage
			Expression variable = varX(new DynamicVariable(className, false))

			// Uncreative method naming is fine
			int methodCount = classNode.methods.size()
			String name = "get${groupName}_${methodCount}"

			// The instance method referenced via script binding
			MethodNode method = new MethodNode(name, ACC_PUBLIC, returnType, [] as Parameter[], [] as ClassNode[], methodBody)
			classNode.addMethod(method)

			return callX(variable, name)
		}

		/**
		 * Turns a list of expressions into a method that returns a list (not a ListExpression)
		 * Works around limitations on list expression literals
		 * (literal "[item0, item1, ...]") being limited to 250 items.
		 * @param expressions the list expressions to be wrapped in a function call
		 * @return call to a method that returns the provided ListExpression
		 */
		@NonNull
		private Expression toExternalListMethod(@NonNull Iterable<Expression> expressions) {
			ListExpression currentListExpression = new ListExpression()

			final int listLimit = 250

			// Local variable in groovy ast is a variable that ... accesses itself.
			VariableExpression variable = varX(createStableUniqueName('extendedList'))
			variable.accessedVariable = variable

			Expression declarationExpression = new DeclarationExpression(variable, ASSIGN, new ListExpression())
			BlockStatement block = block()
			block.addStatement(stmt(declarationExpression))

			int count = 0
			expressions.each { item ->
				if (count++ >= listLimit) {
					count = 1
					block.addStatement(stmt(callX(variable, 'addAll', args(
							asExternalMethodCall('listExpression', ClassHelper.make(Object.class), currentListExpression)
							))))
					currentListExpression = new ListExpression()
				}

				currentListExpression.addExpression(item)
			}

			block.addStatement(stmt(callX(variable, 'addAll', args(
					asExternalMethodCall('listExpression', ClassHelper.make(Object.class), currentListExpression)
					))))
			block.addStatement(returnS(variable))

			return asExternalMethodCall('listExpression', ClassHelper.make(Object.class), block)
		}
	}
}
