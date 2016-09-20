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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Types
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTEnvironment
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNamedArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPositionalArgumentList
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTSingleArgument
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTValue
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTAgent
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTKey
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTNotifications
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPostBuild
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTScriptBlock
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTools
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTTreeStep
import org.jenkinsci.plugins.pipeline.modeldefinition.steps.PipelineModelStep
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.SourceUnitErrorCollector
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import javax.annotation.CheckForNull
import javax.annotation.Nonnull

/**
 * Recursively walks AST tree of parsed Jenkinsfile and builds validation model into {@link ModelASTPipelineDef}
 * reporting any errors as it encounters them.
 *
 * <p>
 * This class has the {@code parseXyz} series of methods and {@code matchXyz} series of methods
 * that both transform an AST node into a specific model object. The difference is that the former
 * reports an error if the input AST node doesn't match the expected form, while the latter returns
 * null under the same circumstance.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
class ModelParser {
    /**
     * Represents the source file being processed.
     */
    private final SourceUnit sourceUnit;

    private final ModelValidator validator

    private final ErrorCollector errorCollector

    public ModelParser(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
        this.errorCollector = new SourceUnitErrorCollector(sourceUnit)
        this.validator = new ModelValidator(errorCollector)
    }

    public @CheckForNull ModelASTPipelineDef parse() {
        return parse(sourceUnit.AST);
    }

    /**
     * Given a Groovy AST that represents a parsed source code, parses
     * that into {@link ModelASTPipelineDef}
     */
    public @CheckForNull ModelASTPipelineDef parse(ModuleNode src) {
        String stepName = StepDescriptor.all().find { it instanceof PipelineModelStep.DescriptorImpl }?.functionName
        // first, quickly ascertain if this module should be parsed at all
        // TODO: 'use script' escape hatch
        def pst = src.statementBlock.statements.find {
            matchMethodCall(it)?.methodAsString == stepName
        }
        if (pst==null)      return null; // no 'pipeline', so this doesn't apply

        ModelASTPipelineDef r = new ModelASTPipelineDef(pst);

        def pipelineBlock = matchBlockStatement(pst);
        if (pipelineBlock==null) {
            // We never get to the validator with this error
            errorCollector.error(r,"Expected a block with the '${stepName}' step")
            return null;
        }

        def sectionsSeen = new HashSet();
        eachStatement(pipelineBlock.body.code) { stmt ->
            def mc = matchMethodCall(stmt);
            if (mc == null) {
                errorCollector.error(r, "Not a valid section definition: '${getSourceText(stmt)}'. Some extra configuration is required.")
            } else {
                def name = parseMethodName(mc);
                // Here, method name is a "section" name at the top level of the "pipeline" closure, which must be unique.
                if (!sectionsSeen.add(name)) {
                    // Also an error that we couldn't actually detect at model evaluation time.
                    errorCollector.error(r, "Multiple occurrences of the $name section")
                }

                switch (name) {
                    case 'stages':
                        r.stages = parseStages(stmt);
                        break;
                    case 'environment':
                        r.environment = parseEnvironment(stmt);
                        break;
                    case 'notifications':
                        r.notifications = parseNotifications(stmt);
                        break;
                    case 'postBuild':
                        r.postBuild = parsePostBuild(stmt);
                        break;
                    case 'agent':
                        r.agent = parseAgent(stmt);
                        break;
                    case 'tools':
                        r.tools = parseTools(stmt)
                        break
                    default:
                        // We need to check for unknowns here.
                        errorCollector.error(r, "Undefined section '$name'")
                }
            }
        }

        r.validate(validator)

        return r;
    }

    public @Nonnull ModelASTStages parseStages(Statement stmt) {
        def r = new ModelASTStages(stmt);

        def m = matchBlockStatement(stmt);
        if (m==null) {
            // Should be able to get this validation later.
            return r
        } else {
            eachStatement(m.body.code) {
                r.stages.add(parseStage(it));
            }
        }
        return r;
    }

    public @Nonnull ModelASTEnvironment parseEnvironment(Statement stmt) {
        def r = new ModelASTEnvironment(stmt);

        def m = matchBlockStatement(stmt);
        if (m==null) {
            // Should be able to get this validation later.
            return r
            //errorCollector.error(r, "Expected a block")
        } else {
            boolean errorEncountered = false
            eachStatement(m.body.code) { s ->
                if (s instanceof ExpressionStatement) {
                    def exp = s.expression;
                    if (exp instanceof BinaryExpression) {
                        if (exp.operation.type == Types.EQUAL) {
                            ModelASTKey key = parseKey(exp.leftExpression)
                            // Necessary check due to keys with identical names being equal.
                            if (r.variables.containsKey(key)) {
                                errorCollector.error(key, "Duplicate environment variable name: '${key.key}'")
                                return
                            } else {
                                r.variables[parseKey(exp.leftExpression)] = parseArgument(exp.rightExpression)
                                return
                            }
                        }
                    }
                }
                errorEncountered = true
            }
            if (errorEncountered) {
                errorCollector.error(r, "Expected name=value pairs")
            }
        }
        return r;
    }

    public @Nonnull ModelASTTools parseTools(Statement stmt) {
        def r = new ModelASTTools(stmt);

        def m = matchBlockStatement(stmt);
        if (m==null) {
            // Should be able to get this validation later.
            return r
        } else {
            eachStatement(m.body.code) { s ->
                def mc = matchMethodCall(s);
                if (mc == null) {
                    // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                    errorCollector.error(r,"Expected to find 'someTool \"someVersion\"'");
                }

                def toolTypeKey = parseKey(mc.method);

                List<Expression> args = ((TupleExpression) mc.arguments).expressions
                if (args.isEmpty()) {
                    errorCollector.error(toolTypeKey, "No argument for tool '${toolTypeKey.key}'")
                } else if (args.size() > 1) {
                    errorCollector.error(toolTypeKey, "Too many arguments for tool '${toolTypeKey.key}'")
                } else {
                    r.tools[toolTypeKey] = parseArgument(args[0])
                }
            }
        }
        return r;
    }

    public @Nonnull ModelASTStage parseStage(Statement stmt) {
        ModelASTStage stage = new ModelASTStage(stmt)
        def m = matchBlockStatement(stmt);
        if (!m?.methodName?.equals("stage")) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(stage,"Expected a stage");
            return stage
        }

        def nameExp = m.getArgument(0);
        if (nameExp==null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(stage,"Expected a stage name but didn't find any");
        }

        stage.name = parseStringLiteral(nameExp)
        return parseStageBody(stage, asBlock(m.body.code));
    }

    /**
     * Given the body of a stage block, attempts to fill in {@link ModelASTStage#branches}.
     *
     * <p>
     * If the body's sole statement is {@code parallel(...)} then it's treated as
     * branches of the stage, or else
     */
    protected ModelASTStage parseStageBody(ModelASTStage stage, BlockStatement block) {
        if (block.statements.size()==1) {
            def parallel = matchParallel(block.statements[0]);
            if (parallel!=null) {
                parallel.args.each { k,v ->
                    stage.branches.add(parseBranch(k, asBlock(v.code)));
                }
                return stage;
            }
        }

        // otherwise it's a single line of execution
        stage.branches.add(parseBranch("default",block));

        return stage;
    }

    /**
     * Parses a block of code into {@link ModelASTBranch}
     */
    public ModelASTBranch parseBranch(String name, BlockStatement body) {
        def b = new ModelASTBranch(body);
        b.name = name
        body.statements.each { st ->
            b.steps.add(parseStep(st));
        }
        return b;
    }

    /**
     * Parses a statement into a {@link ModelASTStep}
     */
    public ModelASTStep parseStep(Statement st) {
        ModelASTStep thisStep = new ModelASTStep(st)
        def mc = matchMethodCall(st);
        if (mc == null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(thisStep,"Expected a step");
            return thisStep
        };

        def stepName = parseMethodName(mc);
        if (stepName.equals("script")) {
            return parseScriptBlock(st)
        }

        List<Expression> args = ((TupleExpression) mc.arguments).expressions

        def bs = matchBlockStatement(st);
        if (bs != null) {
            args = args.subList(0, args.size() - 1)    // cut out the closure argument
            thisStep = new ModelASTTreeStep(st)
            thisStep.name = stepName
            thisStep.args = parseArgumentList(args)
            thisStep.children = eachStatement(bs.body.code) { parseStep(it) }
        } else {
            thisStep.name = stepName
            thisStep.args = parseArgumentList(args)
        }

        return thisStep
    }

    /**
     * Parses a statement into a {@link ModelASTScriptBlock}
     */
    public ModelASTScriptBlock parseScriptBlock(Statement st) {
        ModelASTScriptBlock scriptBlock = new ModelASTScriptBlock(st)
        // TODO: Probably error out for cases with parameters?
        def bs = matchBlockStatement(st);
        if (bs != null) {
            ModelASTSingleArgument groovyBlock = new ModelASTSingleArgument(bs.body)
            groovyBlock.value = ModelASTValue.fromConstant(getSourceText(bs.body.code), bs.body.code)
            scriptBlock.args = groovyBlock
        } else {
            errorCollector.error(scriptBlock, "Script step without a script block")
        }

        return scriptBlock
    }

    /**
     * Parses a statement into a {@link ModelASTAgent}
     */
    public @Nonnull ModelASTAgent parseAgent(Statement st) {
        ModelASTAgent agent = new ModelASTAgent(st)
        def mc = matchMethodCall(st);
        if (mc == null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(agent,"Expected an agent")
        };

        List<Expression> args = ((TupleExpression) mc.arguments).expressions

        agent.args = parseArgumentList(args)

        return agent
    }

    public @Nonnull ModelASTNotifications parseNotifications(Statement stmt) {
        def m = matchBlockStatement(stmt);

        def r = new ModelASTNotifications(stmt);
        if (m==null) {
            errorCollector.error(r, "Expected a block");
        } else {
            eachStatement(m.body.code) {
                r.conditions.add(parseBuildCondition(it));
            }
        }
        return r;
    }

    public @Nonnull ModelASTPostBuild parsePostBuild(Statement stmt) {
        def m = matchBlockStatement(stmt);

        def r = new ModelASTPostBuild(stmt);
        if (m==null) {
            errorCollector.error(r,"Expected a block");
        } else {
            eachStatement(m.body.code) {
                r.conditions.add(parseBuildCondition(it));
            }
        }
        return r;
    }

    public @Nonnull ModelASTBuildCondition parseBuildCondition(Statement st) {
        ModelASTBuildCondition b = new ModelASTBuildCondition(st)
        def m = matchBlockStatement(st);
        if (m == null) {
            errorCollector.error(b,"Expected a build condition")
        } else {
            b.branch = parseBranch("default", asBlock(m.body.code))

            b.condition = m.methodName
        }

        return b
    }

    private ModelASTKey parseKey(Expression e) {
        ModelASTKey key = new ModelASTKey(e)
        key.setKey(parseStringLiteral(e))

        return key
    }

    private ModelASTArgumentList parseArgumentList(List<Expression> args) {
        switch (args.size()) {
        case 0:
            return new ModelASTNamedArgumentList(null);  // no arguments
        case 1:
            def namedArgs = castOrNull(MapExpression, args[0]);
            // Special casing for legacy meta-step syntax, i.e., "[$class: 'Foo', arg1: 'something', ...]" - need to
            // treat that as a single argument but still handle the more standard "foo(arg1: 'something', ...)" case.
            if (namedArgs!=null && !namedArgs.mapEntryExpressions.any { parseKey(it.keyExpression)?.key?.equals('$class') }) {
                def m = new ModelASTNamedArgumentList(args[0]);
                namedArgs.mapEntryExpressions.each { e ->
                    // Don't need to check key duplication here because Groovy compilation will do it for us.
                    m.arguments[parseKey(e.keyExpression)] = parseArgument(e.valueExpression)
                }
                return m;
            } else {
                ModelASTSingleArgument singleArg = new ModelASTSingleArgument(args[0])
                singleArg.value = parseArgument(args[0])
                return singleArg
            }
        default:
            ModelASTPositionalArgumentList l = new ModelASTPositionalArgumentList(args[0]);
            args.each { e ->
                l.arguments.add(parseArgument(e))
            }
            return l
        }
    }

    /**
     * Parse the given expression as an argument to step, etc.
     */
    protected ModelASTValue parseArgument(Expression e) {
        if (e instanceof ConstantExpression) {
            return ModelASTValue.fromConstant(e.value, e)
        }
        if (e instanceof GStringExpression) {
            return ModelASTValue.fromGString(e.text, e)
        }
        if (e instanceof MapExpression) {
            return ModelASTValue.fromGString(getSourceText(e), e)
        }
        if (e instanceof VariableExpression) {
            if (e.name.equals("none")) {
                return ModelASTValue.fromConstant("none", e) // Special casing for agent none.
            } else if (e.name.equals("any")) {
                return ModelASTValue.fromConstant("any", e) // Special casing for agent any.
            }
        }
        // for other composite expressions, treat it as in-place GString
        return ModelASTValue.fromGString("\${"+getSourceText(e)+"}", e)
    }

    protected String parseStringLiteral(Expression exp) {
        def s = matchStringLiteral(exp)
        if (s==null) {
            errorCollector.error(ModelASTValue.fromConstant(null, exp), "Expected string literal")
        }
        return s?:"error";
    }

    protected @CheckForNull String matchStringLiteral(Expression exp) {
        if (exp instanceof ConstantExpression) {
            return castOrNull(String,exp.value);
        }
        // TODO: This may be too broad a way to catch 'agent none' and 'agent any'.
        else if (exp instanceof VariableExpression) {
            return castOrNull(String,exp.name);
        }
        return null;
    }

    /**
     * Accepts literal, GString, function call etc but not other primitives
     */
    protected String parseString(Expression e) {
        if (e instanceof ConstantExpression) {
            if (e.value instanceof String)
                return (String)e.value
            errorCollector.error(ModelASTValue.fromConstant(e.getValue(), e), "Expected string literal but got "+e.value)
            return "error";
        }
        if (e instanceof GStringExpression) {
            return e.text
        }
        // for other composite expressions, treat it as in-place GString
        return "\${"+getSourceText(e)+"}"
    }

    /**
     * Attempts to match a method call of the form {@code foo(...)} and
     * return 'foo' as a string.
     */
    protected @CheckForNull String matchMethodName(MethodCallExpression exp) {
        def lhs = exp.objectExpression;
        if (lhs instanceof VariableExpression) {
            if (lhs.name.equals("this")) {
                return exp.methodAsString; // getMethodAsString() returns null if the method isn't a constant
            }
        }
        return null;
    }

    protected String parseMethodName(MethodCallExpression exp) {
        def s = matchMethodName(exp)
        if (s==null) {
            errorCollector.error(ModelASTValue.fromConstant(null, exp), "Expected a symbol")
            s = "error";
        }
        return s;
    }

    /**
     * Attempts to match AST node as {@link BlockStatementMatch} or
     * return null.
     */
    public @CheckForNull BlockStatementMatch matchBlockStatement(Statement st) {
        def whole = matchMethodCall(st);
        if (whole!=null) {
            def methodName = matchMethodName(whole);
            def args = (TupleExpression)whole.arguments;
            int sz = args.expressions.size();
            if (sz>0 && methodName!=null) {
                def last = args.getExpression(sz - 1);
                if (last instanceof ClosureExpression) {
                    return new BlockStatementMatch(whole,methodName,last);
                }
            }
        }

        return null;
    }

    /**
     * Attempts to match a statement as {@link ParallelMatch} or return null.
     */
    public @CheckForNull ParallelMatch matchParallel(Statement st) {
        def whole = matchMethodCall(st);
        if (whole!=null) {
            def methodName = matchMethodName(whole);
            if ("parallel".equals(methodName)) {
                // beyond this point, if there's mismatch from the expectation we'll throw a problem, instead of returning null

                def args = (TupleExpression)whole.arguments; // list of arguments. in this case it should be just one
                int sz = args.expressions.size();
                def r = new ParallelMatch(whole);
                if (sz==1) {
                    def branches = castOrNull(NamedArgumentListExpression, args.getExpression(sz - 1));
                    if (branches!=null) {
                        for (MapEntryExpression e : branches.mapEntryExpressions) {
                            ClosureExpression value = castOrNull(ClosureExpression, e.valueExpression);
                            if (value == null) {
                                errorCollector.error(new ModelASTKey(e.keyExpression, null), "Expected closure")
                            } else {
                                r.args[parseStringLiteral(e.keyExpression)] = value;
                            }
                        }
                    }
                }
                return r;
            }
        }

        return null;
    }

    /**
     * Works like a regular Java cast, except if the value doesn't match the type, return null
     * instead of throwing an exception.
     */
    protected <X> X castOrNull(Class<X> type, Object value) {
        if (type.isInstance(value))
            return type.cast(value);
        return null;
    }

    /**
     * Normalizes a statement to a block of statement by creating a wrapper if need be.
     */
    protected BlockStatement asBlock(Statement st) {
        if (st instanceof BlockStatement) {
            return st;
        } else {
            def bs = new BlockStatement();
            bs.addStatement(st);
            return bs;
        }
    }

    protected <T> List<T> eachStatement(Statement st, @ClosureParams(FirstParam.class) Closure<T> c) {
        return asBlock(st).statements.collect(c)
    }

    /**
     * Attempts to match a given statement as a method call, or return null
     */
    protected @CheckForNull MethodCallExpression matchMethodCall(Statement st) {
        if (st instanceof ExpressionStatement) {
            def exp = st.expression;
            if (exp instanceof MethodCallExpression) {
                return exp;
            }
        }
        return null;
    }

    protected String getSourceText(BinaryExpression e) {
        return getSourceText(e.leftExpression) + e.operation.getText() + getSourceText(e.rightExpression)
    }

    /**
     * Obtains the source text of the given {@link org.codehaus.groovy.ast.ASTNode}.
     */
    protected String getSourceText(ASTNode n) {
        def result = new StringBuilder();
        for (int x = n.getLineNumber(); x <= n.getLastLineNumber(); x++) {
            String line = sourceUnit.source.getLine(x, null);
            if (line == null)
                throw new AssertionError("Unable to get source line"+x);

            if (x == n.getLastLineNumber()) {
                line = line.substring(0, n.getLastColumnNumber() - 1);
            }
            if (x == n.getLineNumber()) {
                line = line.substring(n.getColumnNumber() - 1);
            }
            result.append(line).append('\n');
        }

        return result.toString().trim();
    }
}
