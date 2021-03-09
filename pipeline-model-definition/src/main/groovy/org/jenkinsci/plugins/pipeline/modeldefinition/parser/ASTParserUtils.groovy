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

import edu.umd.cs.findbugs.annotations.CheckForNull
import edu.umd.cs.findbugs.annotations.NonNull
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import hudson.model.Describable
import hudson.model.Descriptor
import hudson.model.JobProperty
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.jenkinsci.plugins.pipeline.modeldefinition.CommonUtils
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.ModelStepLoader
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

/**
 * Misc. utilities used across both {@link ModelParser} and {@link RuntimeASTTransformer}.
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class ASTParserUtils {

	/**
	 * Attempts to match a method call of the form {@code foo(...)} and
	 * return 'foo' as a string.
	 */
	static @CheckForNull String matchMethodName(MethodCallExpression exp) {
		def lhs = exp.objectExpression
		if (lhs instanceof VariableExpression) {
			if (lhs.name == "this") {
				return exp.methodAsString // getMethodAsString() returns null if the method isn't a constant
			}
		}
		return null
	}

	// TODO: Remove or otherwise cleanup so that it's not always firing!
	static String printer(String s, int ind) {
		return "${' ' * ind * 2}${s}"
	}

	// TODO: Remove or otherwise cleanup so that it's not always firing!
	static String prettyPrint(ASTNode n, int ind = -1) {
		List<String> s = []

		ind++
		if (n instanceof ReturnStatement) {
			s << printer("- return:", ind)
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof ArgumentListExpression) {
			s << printer("- args:", ind)
			n.expressions.each { s << prettyPrint(it, ind) }
		} else if (n instanceof ClosureExpression) {
			s << printer("- closure:", ind)
			s << prettyPrint(n.code, ind)
		} else if (n instanceof BlockStatement) {
			s << printer("- block", ind)
			n.statements.each {
				s << prettyPrint(it, ind)
			}
		} else if (n instanceof ConstructorCallExpression) {
			s << printer("- constructor of ${n.type.name}:", ind)
			n.arguments.each {
				s << prettyPrint(it, ind)
			}
		} else if (n instanceof MapExpression) {
			s << printer("- map:", ind)
			n.mapEntryExpressions.each {
				s << prettyPrint(it, ind)
			}
		} else if (n instanceof ListExpression) {
			s << printer("- list:", ind)
			n.expressions.each {
				s << prettyPrint(it, ind)
			}
		} else if (n instanceof StaticMethodCallExpression) {
			s << printer("- static method '${n.method}':", ind)
			s << prettyPrint(n.receiver, ind)
			s << prettyPrint(n.arguments, ind)
		} else if (n instanceof MethodCallExpression) {
			s << printer("- method '${n.method}':", ind)
			s << prettyPrint(n.receiver, ind)
			s << prettyPrint(n.arguments, ind)
		}else if (n instanceof MapEntryExpression) {
			s << prettyPrint(n.keyExpression, ind)
			s << prettyPrint(n.valueExpression, ind)
		} else if (n instanceof ExpressionStatement) {
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof GStringExpression) {
			s << printer("- gstring:", ind)
			ind++
			s << printer("- strings:", ind)
			n.strings.each { s << prettyPrint(it, ind) }
			s << printer("- values:", ind)
			n.values.each { s << prettyPrint(it, ind) }
		} else if (n instanceof PropertyExpression) {
			s << printer("- property:", ind)
			s << prettyPrint(n.objectExpression, ind)
			s << prettyPrint(n.property, ind)
		} else if (n instanceof ModuleNode) {
			s << printer("- module:", ind)
			ind++
			s << printer("- methods:", ind)
			n.methods.each { s << prettyPrint(it, ind) }
			s << printer("- statements:", ind)
			n.statementBlock.statements.each { s << prettyPrint(it, ind) }
			s << printer("- classes:", ind)
			n.classes.each { s << prettyPrint(it, ind) }
		} else if (n instanceof MethodNode) {
			s << printer("- methodNode:", ind)
			s << prettyPrint(n.code, ind)
		} else if (n instanceof ThrowStatement) {
			s << printer("- throw:", ind)
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof CastExpression) {
			s << printer("- cast:", ind)
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof VariableExpression) {
			s << printer("- var:", ind)
			s << printer("  - name: ${n.name}", ind)
			s << printer("  - accessedVariable: ${n.accessedVariable}", ind)
		} else if (n instanceof PrefixExpression) {
			s << printer("- prefix (${n.operation}):", ind)
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof PostfixExpression) {
			s << printer("- postfix (${n.operation}):", ind)
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof ElvisOperatorExpression) {
			s << printer("- elvis:", ind)
			s << prettyPrint(n.trueExpression, ind)
			s << prettyPrint(n.falseExpression, ind)
		} else if (n instanceof BinaryExpression) {
			s << printer("- binary:", ind)
			s << printer("  - left:", ind)
			s << prettyPrint(n.leftExpression, ind)
			s << printer(" - op: ${n.operation.toString()}", ind)
			s << printer("  - right:", ind)
			s << prettyPrint(n.rightExpression, ind)
		} else if (n instanceof DeclarationExpression) {
			s << printer("- decl:", ind)
			s << printer("  - left:", ind)
			s << prettyPrint(n.leftExpression, ind)
			s << printer(" - op: ${n.operation.toString()}", ind)
			s << printer("  - right:", ind)
			s << prettyPrint(n.rightExpression, ind)
		} else if (n instanceof IfStatement) {
			s << printer("- if stmt:", ind)
			s << prettyPrint(n.booleanExpression, ind)
			s << printer("- if block:", ind)
			s << prettyPrint(n.ifBlock, ind)
			if (n.elseBlock != null) {
				s << printer("- else block:", ind)
				s << prettyPrint(n.elseBlock, ind )
			}
		} else if (n instanceof TupleExpression) {
			s << printer("- tuples:", ind)
			n.expressions.each {
				s << prettyPrint(it, ind)
			}
		} else if (n instanceof NotExpression) {
			s << printer("- not:", ind)
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof BooleanExpression) {
			s << printer("- boolexp:", ind)
			s << prettyPrint(n.expression, ind)
		} else if (n instanceof TernaryExpression) {
			s << printer("- ternary:", ind)
			s << printer("- bool:", ind + 1)
			s << prettyPrint(n.booleanExpression, ind + 1)
			s << printer("- true:", ind + 1)
			s << prettyPrint(n.trueExpression, ind + 1)
			s << printer("- false:", ind + 1)
			s << prettyPrint(n.falseExpression, ind + 1)
		} else if (n instanceof ClassNode && (n.name == "WorkflowScript" || n.name.startsWith("generated."))) {
			s << printer("- class: ${n.name}", ind)
			s << printer("- methods:", ind + 1)
			n.methods.each { m ->
				s << prettyPrint(m, ind + 1)
			}
			s << printer("- properties:", ind + 1)
			n.properties.each { p ->
				s << prettyPrint(p, ind + 1)
			}
			s << printer("- object initializers:", ind + 1)
			n.objectInitializerStatements.each { o ->
				s << prettyPrint(o, ind + 1)
			}
		} else {
			s << printer("- ${n}", ind)
		}

		return s.join("\n")
	}

	/**
	 * Splits out and returns the {@link BlockStatementMatch} corresponding to  the given {@link MethodCallExpression}.
	 */
	@CheckForNull
	static BlockStatementMatch blockStatementFromExpression(@NonNull MethodCallExpression exp) {
		def methodName = matchMethodName(exp)
		def args = (TupleExpression)exp.arguments
		int sz = args.expressions.size()
		if (sz>0 && methodName!=null) {
			def last = args.getExpression(sz - 1)
			if (last instanceof ClosureExpression) {
				return new BlockStatementMatch(exp,methodName,last)
			}
		}

		return null
	}

	/**
	 * Normalizes a statement to a block of statement by creating a wrapper if need be.
	 */
	static BlockStatement asBlock(Statement st) {
		if (st instanceof BlockStatement) {
			return st
		} else {
			def bs = new BlockStatement()
			bs.addStatement(st)
			return bs
		}
	}

	/**
	 * Attempts to match a given statement as a method call, or return null
	 */
	static @CheckForNull MethodCallExpression matchMethodCall(Statement st) {
		if (st instanceof ExpressionStatement) {
			def exp = st.expression
			if (exp instanceof MethodCallExpression) {
				return exp
			}
		}
		return null
	}

	/**
	 * Takes a statement and iterates over its contents - if the statement is not a {@link BlockStatement}, it gets
	 * wrapped in a new block to simplify iteration.
	 */
	static <T> List<T> eachStatement(Statement st, @ClosureParams(FirstParam.class) Closure<T> c) {
		return asBlock(st).statements.collect(c)
	}

	/**
	 * Attempts to match AST node as {@link BlockStatementMatch} or
	 * return null.
	 */
	static @CheckForNull BlockStatementMatch matchBlockStatement(Statement st) {
		def whole = matchMethodCall(st)
		if (whole!=null) {
			return blockStatementFromExpression(whole)
		}

		return null
	}

	/**
	 * Takes a list of {@link ModelASTElement}s corresponding to {@link Describable}s (such as {@link JobProperty}s, etc),
	 * and transforms their Groovy AST nodes into AST from {@link #methodCallToDescribable(MethodCallExpression,Class)}.
	 */
	@NonNull
	static Expression transformListOfDescribables(@CheckForNull List<ModelASTElement> children, Class<? extends Describable> descClass) {
		ListExpression descList = new ListExpression()

		children?.each { d ->
			if (d.sourceLocation instanceof Statement) {
				MethodCallExpression m = matchMethodCall((Statement) d.sourceLocation)
				if (m != null) {
					descList.addExpression(methodCallToDescribable(m,descClass))
				} else {
					throw new IllegalArgumentException("Expected a method call expression but received ${d.sourceLocation}")
				}
			} else {
				throw new IllegalArgumentException("Expected a statement but received ${d.sourceLocation}")
			}
		}

		return descList
	}

	/**
	 * Transforms a container for describables, such as {@link Triggers}, into AST for instantation.
	 * @param original A {@link ModelASTElement} such as {@link ModelASTTriggers} or {@link ModelASTBuildParameters}
	 * @param children The children for the original element - passed as a separate argument since the getter will
	 * be different.
	 * @param containerClass The class we will be instantiating, i.e., {@link Parameters} or {@link Triggers}.
	 * @param descClass the describable class we're inheriting from
	 * @return The AST for instantiating the container and its contents.
	 */
	static Expression transformDescribableContainer(@CheckForNull ModelASTElement original,
			@CheckForNull List<ModelASTElement> children,
			@NonNull Class containerClass,
			@NonNull Class<? extends Describable> descClass) {
		if (isGroovyAST(original) && !children?.isEmpty()) {
			return ctorX(ClassHelper.make(containerClass), args(transformListOfDescribables(children, descClass)))
		}
		return constX(null)
	}

	/**
	 * Transform a when condition, and its children if any exist, into instantiation AST.
	 */
	static Expression transformWhenContentToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
		if (original instanceof ModelASTElement && (isGroovyAST((ModelASTElement)original) || original instanceof InvisibleGlobalWhenCondition)) {
			DeclarativeStageConditionalDescriptor parentDesc =
					(DeclarativeStageConditionalDescriptor) SymbolLookup.get().findDescriptor(
					DeclarativeStageConditional.class, original.name)
			if (original instanceof ModelASTWhenCondition) {
				ModelASTWhenCondition cond = (ModelASTWhenCondition) original
				if (cond.children.isEmpty()) {
					if (cond.getSourceLocation() != null && cond.getSourceLocation() instanceof Statement) {
						MethodCallExpression methCall = matchMethodCall((Statement) cond.getSourceLocation())

						if (methCall != null) {
							return methodCallToDescribable(methCall, null)
						}
					} else {
						return parentDesc.transformToRuntimeAST(original)
					}
				} else {
					MapExpression argMap = new MapExpression()
					if (parentDesc.allowedChildrenCount == 1) {
						argMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY),
								transformWhenContentToRuntimeAST(cond.children.first()))
					} else {
						argMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY),
								new ListExpression(cond.children.collect { transformWhenContentToRuntimeAST(it) }))
					}
					return callX(ClassHelper.make(CommonUtils.class),
							"instantiateDescribable",
							args(
							classX(parentDesc.clazz),
							argMap
							))
				}
			} else if (original instanceof ModelASTWhenExpression) {
				return parentDesc.transformToRuntimeAST(original)
			}
		}
		return constX(null)
	}

	/**
	 * Transforms the AST for a "mapped closure" - i.e., a closure of "foo 'bar'" method calls - into a
	 * {@link MapExpression}. Recurses for nested "mapped closures" as well.
	 * @param original a possibly null {@link ClosureExpression} to transform
	 * @return A {@link MapExpression}, or null if the original expression was null.
	 */
	@CheckForNull
	static MapExpression recurseAndTransformMappedClosure(@CheckForNull ClosureExpression original) {
		if (original != null) {
			MapExpression mappedClosure = new MapExpression()
			eachStatement(original.code) { s ->
				MethodCallExpression mce = matchMethodCall(s)
				if (mce != null) {
					List<Expression> args = methodCallArgs(mce)
					if (args.size() == 1) {
						Expression singleArg = args.get(0)
						if (singleArg instanceof ClosureExpression) {
							mappedClosure.addMapEntryExpression(mce.method, recurseAndTransformMappedClosure(singleArg))
						} else {
							mappedClosure.addMapEntryExpression(mce.method, singleArg)
						}
					}
				}
			}
			return mappedClosure
		}

		return null
	}

	/**
	 * Takes a list of expressions used as arguments that could contain describables, and creates a MapExpression
	 * suitable for DescribableModel.instantiate.
	 * @param args A list of arguments
	 * @return A MapExpression
	 */
	@CheckForNull
	static Expression argsMap(List<Expression> args) {
		MapExpression tmpMap = new MapExpression()
		args.each { singleArg ->
			if (singleArg instanceof MapExpression) {
				singleArg.mapEntryExpressions.each { entry ->
					if (entry.valueExpression instanceof MethodCallExpression) {
						MethodCallExpression m = (MethodCallExpression) entry.valueExpression
						tmpMap.addMapEntryExpression(entry.keyExpression, methodCallToDescribable(m, null))
					} else {
						tmpMap.addMapEntryExpression(entry)
					}
				}
			} else {
				if (singleArg instanceof MethodCallExpression) {
					tmpMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY),
							methodCallToDescribable(singleArg, null))
				} else {
					tmpMap.addMapEntryExpression(constX(UninstantiatedDescribable.ANONYMOUS_KEY), singleArg)
				}
			}
		}

		return tmpMap
	}

	/**
	 * A shortcut for taking the Expression at MethodCallExpression.arguments and turning it into a list of Expressions.
	 * @param expr A method call
	 * @return A possibly empty list of expressions
	 */
	@NonNull
	static List<Expression> methodCallArgs(@NonNull MethodCallExpression expr) {
		return ((TupleExpression) expr.arguments).expressions
	}

	/**
	 * Transforms a {@link MethodCallExpression} into either a map of name and arguments for steps, or a call to
	 * {@link org.jenkinsci.plugins.pipeline.modeldefinition.CommonUtils#instantiateDescribable(Class < T >, Map < String, } that can be invoked at runtime to actually instantiated.
	 * @param expr A method call.
	 * @param descClass possibly null describable parent class
	 * @return The appropriate transformation, or the original expression if it didn't correspond to a Describable.
	 */
	@CheckForNull
	static Expression methodCallToDescribable(MethodCallExpression expr, Class<? extends Describable> descClass) {
		def methodName = matchMethodName(expr)
		List<Expression> methArgs = methodCallArgs(expr)

		DescriptorLookupCache lookupCache = DescriptorLookupCache.getPublicCache()

		Descriptor<? extends Describable> funcDesc = lookupCache.lookupFunction(methodName, descClass)
		StepDescriptor stepDesc = lookupCache.lookupStepDescriptor(methodName)
		// This is the case where we've got a wrapper in options
		if (stepDesc != null ||
		(funcDesc != null && !StepDescriptor.metaStepsOf(methodName)?.isEmpty())) {
			MapExpression m = new MapExpression()
			m.addMapEntryExpression(constX("name"), constX(methodName))
			m.addMapEntryExpression(constX("args"), argsMap(methArgs))
			return m
		} else if (funcDesc != null) {
			// Ok, now it's a non-executable descriptor. Phew.
			Class<? extends Describable> descType = funcDesc.clazz

			return callX(ClassHelper.make(CommonUtils.class), "instantiateDescribable",
					args(classX(descType), argsMap(methArgs)))
		} else {
			// Not a describable at all!
			return expr
		}
	}

	/**
	 * Determine whether this element can be used for Groovy AST transformation
	 * @param original
	 * @return True if the element isn't null, it has a source location, and that source location is an {@link ASTNode}
	 */
	static boolean isGroovyAST(ModelASTElement original) {
		return original != null && original.sourceLocation != null && original.sourceLocation instanceof ASTNode
	}

	static boolean blockHasMethod(BlockStatement block, String methodName) {
		if (block != null) {
			return block.statements.any {
				MethodCallExpression expr = matchMethodCall(it)
				return expr != null && matchMethodName(expr) == methodName
			}
		} else {
			return false
		}
	}

	static boolean isDeclarativePipelineStep(Statement stmt, boolean topLevel = true) {
		def b = matchBlockStatement(stmt)

		if (b != null &&
		b.methodName == ModelStepLoader.STEP_NAME &&
		b.arguments.expressions.size() == 1) {
			BlockStatement block = asBlock(b.body.code)
			if (topLevel) {
				// If we're in a Jenkinsfile, we want to find any pipeline block at the top-level
				return block != null
			} else {
				// If we're in a shared library, filter out anything that doesn't have agent and stages method calls
				def hasAgent = blockHasMethod(block, "agent")
				def hasStages = blockHasMethod(block, "stages")
				return hasAgent && hasStages
			}
		}

		return false
	}

}
