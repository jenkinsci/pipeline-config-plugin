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
import hudson.model.Describable
import hudson.model.Descriptor
import jenkins.model.Jenkins
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.Types
import org.jenkinsci.plugins.pipeline.modeldefinition.DescriptorLookupCache
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.*
import org.jenkinsci.plugins.pipeline.modeldefinition.ModelStepLoader
import org.jenkinsci.plugins.pipeline.modeldefinition.model.BuildCondition
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Options
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Parameters
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Triggers
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ErrorCollector
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidatorImpl
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.SourceUnitErrorCollector
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.DescribableParameter

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
class ModelParser implements Parser {
    /**
     * Represents the source file being processed.
     */
    private final SourceUnit sourceUnit;

    private final ModelValidator validator

    private final ErrorCollector errorCollector

    private final DescriptorLookupCache lookup

    public ModelParser(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
        this.errorCollector = new SourceUnitErrorCollector(sourceUnit)
        this.validator = new ModelValidatorImpl(errorCollector)
        this.lookup = DescriptorLookupCache.getPublicCache()
    }

    public @CheckForNull ModelASTPipelineDef parse() {
        return parse(sourceUnit.AST);
    }

    public @CheckForNull List<ModelASTStep> parsePlainSteps(ModuleNode src) {
        src.statementBlock.statements.collect() {
            return parseStep(it);
        }
    }

    public void checkForNestedPipelineStep(Statement statement) {
        def b = matchBlockStatement(statement)
        if (b != null) {
            if (b.methodName == ModelStepLoader.STEP_NAME) {
                ModelASTPipelineDef p = new ModelASTPipelineDef(statement)
                errorCollector.error(p, Messages.ModelParser_PipelineBlockNotAtTop(ModelStepLoader.STEP_NAME))
            }
            eachStatement(b.body.code) { s ->
                checkForNestedPipelineStep(s)
            }
        }
    }

    /**
     * Given a Groovy AST that represents a parsed source code, parses
     * that into {@link ModelASTPipelineDef}
     */
    public @CheckForNull ModelASTPipelineDef parse(ModuleNode src) {
        // first, quickly ascertain if this module should be parsed at all
        // TODO: 'use script' escape hatch
        def pst = src.statementBlock.statements.find {
            matchMethodCall(it)?.methodAsString == ModelStepLoader.STEP_NAME
        }

        if (pst==null) {
            // Check if there's a 'pipeline' step somewhere nested within the other statements and error out if that's the case.
            src.statementBlock.statements.each { checkForNestedPipelineStep(it) }
            return null; // no 'pipeline', so this doesn't apply
        }

        ModelASTPipelineDef r = new ModelASTPipelineDef(pst);

        def pipelineBlock = matchBlockStatement(pst);
        if (pipelineBlock==null) {
            // We never get to the validator with this error
            errorCollector.error(r, Messages.ModelParser_PipelineStepWithoutBlock(ModelStepLoader.STEP_NAME))
            return null;
        }

        def sectionsSeen = new HashSet();
        eachStatement(pipelineBlock.body.code) { stmt ->
            def mc = matchMethodCall(stmt);
            if (mc == null) {
                errorCollector.error(r, Messages.ModelParser_InvalidSectionDefinition(getSourceText(stmt)))
            } else {
                def name = parseMethodName(mc);
                // Here, method name is a "section" name at the top level of the "pipeline" closure, which must be unique.
                if (!sectionsSeen.add(name)) {
                    // Also an error that we couldn't actually detect at model evaluation time.
                    errorCollector.error(r, Messages.Parser_MultipleOfSection(name))
                }

                switch (name) {
                    case 'stages':
                        r.stages = parseStages(stmt);
                        break;
                    case 'environment':
                        r.environment = parseEnvironment(stmt);
                        break;
                    case 'post':
                        r.postBuild = parsePostBuild(stmt);
                        break;
                    case 'agent':
                        r.agent = parseAgent(stmt);
                        break;
                    case 'tools':
                        r.tools = parseTools(stmt)
                        break
                    case 'options':
                        r.options = parseOptions(stmt)
                        break
                    case 'parameters':
                        r.parameters = parseBuildParameters(stmt)
                        break
                    case 'triggers':
                        r.triggers = parseTriggers(stmt)
                        break
                    case 'properties':
                        errorCollector.error(r, Messages.ModelParser_RenamedProperties())
                        break
                    case 'wrappers':
                        errorCollector.error(r, "The 'wrappers' section has been removed as of version 0.8. Use 'options' instead.")
                        break
                    case 'jobProperties':
                        errorCollector.error(r, Messages.ModelParser_RenamedJobProperties())
                        break
                    case 'notifications':
                        errorCollector.error(r, Messages.ModelParser_RenamedNotifications())
                        break
                    case 'postBuild':
                        errorCollector.error(r, Messages.ModelParser_RenamedPostBuild())
                        break
                    default:
                        // We need to check for unknowns here.
                        errorCollector.error(r, Messages.Parser_UndefinedSection(name))
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
                                errorCollector.error(key, Messages.ModelParser_DuplicateEnvVar(key.key))
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
                errorCollector.error(r, Messages.ModelParser_ExpectedNVPairs())
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
                    errorCollector.error(r,Messages.ModelParser_ExpectedTool());
                }

                def toolTypeKey = parseKey(mc.method);

                List<Expression> args = ((TupleExpression) mc.arguments).expressions
                if (args.isEmpty()) {
                    errorCollector.error(toolTypeKey, Messages.ModelParser_NoArgForTool(toolTypeKey.key))
                } else if (args.size() > 1) {
                    errorCollector.error(toolTypeKey, Messages.ModelParser_TooManyArgsForTool(toolTypeKey.key))
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
            errorCollector.error(stage, Messages.ModelParser_ExpectedStage());
            return stage
        }

        def nameExp = m.getArgument(0);
        if (nameExp==null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(stage, Messages.ModelParser_ExpectedStageName());
        }

        stage.name = parseStringLiteral(nameExp)
        def sectionsSeen = new HashSet()
        def bodyExp = m.getArgument(1)
        if (bodyExp == null || !(bodyExp instanceof ClosureExpression)) {
            errorCollector.error(stage, Messages.ModelParser_StageWithoutBlock())
        } else {
            eachStatement(((ClosureExpression)bodyExp).code) { s ->
                def mc = matchMethodCall(s);
                if (mc == null) {
                    errorCollector.error(stage, Messages.ModelParser_InvalidStageSectionDefinition(getSourceText(s)))
                } else {
                    def name = parseMethodName(mc);

                    // Here, method name is a "section" name in the "stage" closure, which must be unique.
                    if (!sectionsSeen.add(name)) {
                        // Also an error that we couldn't actually detect at model evaluation time.
                        errorCollector.error(stage, Messages.Parser_MultipleOfSection(name))
                    }
                    switch (name) {
                        case 'agent':
                            stage.agent = parseAgent(s)
                            break
                        case 'when':
                            stage.when = parseWhen(s)
                            break
                        case 'steps':
                            def stepsBlock = matchBlockStatement(s);
                            BlockStatement block = asBlock(stepsBlock.body.code)

                            // Handle parallel as a special case
                            if (block.statements.size()==1) {
                                def parallel = matchParallel(block.statements[0]);

                                if (parallel != null) {
                                    parallel.args.each { k, v ->
                                        stage.branches.add(parseBranch(k, asBlock(v.code)));
                                    }
                                    stage.failFast = parallel.failFast
                                } else {
                                    // otherwise it's a single line of execution
                                    stage.branches.add(parseBranch("default", block));
                                }
                            } else {
                                // otherwise it's a single line of execution
                                stage.branches.add(parseBranch("default", block));
                            }
                            break
                        case 'post':
                            stage.post = parsePostStage(s)
                            break;
                        case 'tools':
                            stage.tools = parseTools(s)
                            break
                        case 'environment':
                            stage.environment = parseEnvironment(s)
                            break
                        default:
                            errorCollector.error(stage, Messages.ModelParser_UnknownStageSection(name))
                    }
                }
            }
        }
        return stage
    }

    public ModelASTWhen parseWhen(Statement statement) {
        def stepsBlock = matchBlockStatement(statement)
        BlockStatement block = asBlock(stepsBlock.body.code)
        ModelASTWhen w = new ModelASTWhen(statement)
        block.statements.each {s ->
            w.conditions.add(parseStep(s))
        }

        return w
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
     * Parses a block of code into {@link ModelASTOptions}
     */
    public ModelASTOptions parseOptions(Statement stmt) {
        def o = new ModelASTOptions(stmt);
        def m = matchBlockStatement(stmt);
        if (m == null) {
            // Should be able to get this validation later.
            return o
        } else {
            eachStatement(m.body.code) { s ->
                o.options.add(parseOption(s));
            }
        }
        return o;
    }

    private <T extends ModelASTMethodCall> T handleNullMethodCall(T element, Statement st, String sectionName,
                                                                  String expectedError, Collection<String> possibles) {
        if (st instanceof ExpressionStatement && st.expression instanceof MapExpression) {
            errorCollector.error(element, Messages.ModelParser_MapNotAllowed(sectionName))
        } else if (st instanceof ExpressionStatement &&
            st.expression instanceof VariableExpression &&
            ((VariableExpression)st.expression).name in possibles) {
            element.name = ((VariableExpression)st.expression).name
        } else {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
            errorCollector.error(element, expectedError);
        }

        return element
    }

    /**
     * Parses a statement into a {@link ModelASTOption}
     */
    public ModelASTOption parseOption(Statement st) {
        ModelASTOption thisOpt = new ModelASTOption(st)
        def mc = matchMethodCall(st);
        if (mc == null) {
            return handleNullMethodCall(thisOpt, st, Messages.Parser_Options(), Messages.ModelParser_ExpectedOption(),
                Options.allowedOptionTypes.keySet())
        };

        def bs = matchBlockStatement(st);
        if (bs != null) {
            errorCollector.error(thisOpt, Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Options()))
            return thisOpt
        } else {
            ModelASTMethodCall mArgs = parseMethodCall(mc)
            thisOpt.args = mArgs.args
            thisOpt.name = mArgs.name
        }

        return thisOpt
    }

    /**
     * Parses a block of code into {@link ModelASTTriggers}
     */
    public ModelASTTriggers parseTriggers(Statement stmt) {
        def triggers = new ModelASTTriggers(stmt);
        def m = matchBlockStatement(stmt);
        if (m == null) {
            // Should be able to get this validation later.
            return triggers
        } else {
            eachStatement(m.body.code) { s ->
                triggers.triggers.add(parseTrigger(s));
            }
        }
        return triggers;
    }

    /**
     * Parses a statement into a {@link ModelASTTrigger}
     */
    public ModelASTTrigger parseTrigger(Statement st) {
        ModelASTTrigger trig = new ModelASTTrigger(st)
        def mc = matchMethodCall(st);
        if (mc == null) {
            return handleNullMethodCall(trig, st, Messages.Parser_Triggers(),
                Messages.ModelParser_ExpectedTrigger(), Triggers.allowedTriggerTypes.keySet())
        };

        def bs = matchBlockStatement(st);
        if (bs != null) {
            errorCollector.error(trig, Messages.ModelParser_CannotHaveBlocks(Messages.Parser_Triggers()))
            return trig
        } else {
            ModelASTMethodCall mArgs = parseMethodCall(mc)
            trig.args = mArgs.args
            trig.name = mArgs.name
        }

        return trig
    }

    /**
     * Parses a block of code into {@link ModelASTBuildParameters}
     */
    public ModelASTBuildParameters parseBuildParameters(Statement stmt) {
        def bp = new ModelASTBuildParameters(stmt);
        def m = matchBlockStatement(stmt);
        if (m == null) {
            // Should be able to get this validation later.
            return bp
        } else {
            eachStatement(m.body.code) { s ->
                bp.parameters.add(parseBuildParameter(s));
            }
        }
        return bp;
    }

    /**
     * Parses a statement into a {@link ModelASTBuildParameter}
     */
    public ModelASTBuildParameter parseBuildParameter(Statement st) {
        ModelASTBuildParameter param = new ModelASTBuildParameter(st)
        def mc = matchMethodCall(st);
        if (mc == null) {
            return handleNullMethodCall(param, st, Messages.Parser_BuildParameters(),
                Messages.ModelParser_ExpectedBuildParameter(), Parameters.allowedParameterTypes.keySet())
        };

        def bs = matchBlockStatement(st);
        if (bs != null) {
            errorCollector.error(param, Messages.ModelParser_CannotHaveBlocks(Messages.Parser_BuildParameters()))
            return param
        } else {
            ModelASTMethodCall mArgs = parseMethodCall(mc)
            param.args = mArgs.args
            param.name = mArgs.name
        }

        return param
    }

    public ModelASTMethodCall parseMethodCall(MethodCallExpression expr) {
        ModelASTMethodCall m = new ModelASTMethodCall(expr)
        def methodName = parseMethodName(expr);
        m.name = methodName

        List<Expression> args = ((TupleExpression) expr.arguments).expressions

        args.each { a ->
            def namedArgs = castOrNull(MapExpression, a);
            if (namedArgs != null) {
                namedArgs.mapEntryExpressions.each { e ->
                    // Don't need to check key duplication here because Groovy compilation will do it for us.
                    ModelASTKeyValueOrMethodCallPair keyPair = new ModelASTKeyValueOrMethodCallPair(e)
                    keyPair.key = parseKey(e.keyExpression)
                    if (e.valueExpression instanceof ClosureExpression) {
                        errorCollector.error(keyPair, Messages.ModelParser_MethodCallWithClosure())
                    } else if (e.valueExpression instanceof MethodCallExpression) {
                        keyPair.value = parseMethodCall((MethodCallExpression) e.valueExpression)
                    } else {
                        keyPair.value = parseArgument(e.valueExpression)
                    }
                    m.args << keyPair
                }
            } else if (a instanceof ClosureExpression) {
                errorCollector.error(m, Messages.ModelParser_MethodCallWithClosure())
            } else if (a instanceof MethodCallExpression) {
                m.args << parseMethodCall(a)
            } else {
                m.args << parseArgument(a)
            }
        }

        return m
    }

    public ModelASTClosureMap parseClosureMap(ClosureExpression expression) {
        ModelASTClosureMap map = new ModelASTClosureMap(expression)

        eachStatement(expression.code) { s ->
            def mc = matchMethodCall(s);
            if (mc == null) {
                // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                errorCollector.error(map, Messages.ModelParser_ExpectedMapMethod());
            } else {

                def k = parseKey(mc.method);

                List<Expression> args = ((TupleExpression) mc.arguments).expressions
                if (args.isEmpty()) {
                    errorCollector.error(k, Messages.ModelParser_NoArgForMapMethodKey(k.key))
                } else if (args.size() > 1) {
                    errorCollector.error(k, Messages.ModelParser_TooManyArgsForMapMethodKey(k.key))
                } else if (args[0] instanceof ClosureExpression) {
                    map.variables[k] = parseClosureMap((ClosureExpression) args[0])
                } else {
                    map.variables[k] = parseArgument(args[0])
                }
            }
        }

        return map
    }

    /**
     * Parses a statement into a {@link ModelASTStep}
     */
    public ModelASTStep parseStep(Statement st) {
        ModelASTStep thisStep = new ModelASTStep(st)
        def mc = matchMethodCall(st);
        if (mc == null) {
            // Not sure of a better way to deal with this - it's a full-on parse-time failure.
             errorCollector.error(thisStep, Messages.ModelParser_ExpectedStep());
            return thisStep
        };

        def stepName = parseMethodName(mc);
        if (stepName == "script") {
            return parseScriptBlock(st)
        } else if (stepName == "expression") {
            return parseWhenExpression(st)
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
            thisStep.args = populateStepArgumentList(thisStep, parseArgumentList(args))
        }

        return thisStep
    }

    private ModelASTArgumentList populateStepArgumentList(final ModelASTStep step, final ModelASTArgumentList origArgs) {
        if (Jenkins.getInstance() != null && origArgs instanceof ModelASTSingleArgument) {
            ModelASTValue singleArgValue = ((ModelASTSingleArgument)origArgs).value
            ModelASTNamedArgumentList namedArgs = new ModelASTNamedArgumentList(origArgs.sourceLocation)
            Descriptor<? extends Describable> desc = lookup.lookupStepOrFunction(step.name)
            DescribableModel<? extends Describable> model = lookup.modelForStepOrFunction(step.name)

            if (model != null) {
                DescribableParameter p = model.soleRequiredParameter

                if (p != null && !lookup.stepTakesClosure(desc)) {
                    ModelASTKey paramKey = new ModelASTKey(step.sourceLocation)
                    paramKey.key = p.name
                    namedArgs.arguments.put(paramKey, singleArgValue)

                    return namedArgs
                }
            }
        }

        return origArgs
    }

    public ModelASTWhenExpression parseWhenExpression(Statement st) {
        return parseCodeBlockInternal(st, new ModelASTWhenExpression(st), "When")
    }

    /**
     * Parses a statement into a {@link ModelASTScriptBlock}
     */
    public ModelASTScriptBlock parseScriptBlock(Statement st) {
        return parseCodeBlockInternal(st, new ModelASTScriptBlock(st), "Script")
    }

    private <T extends AbstractModelASTCodeBlock> T parseCodeBlockInternal(Statement st, T scriptBlock, String pronoun) {
        // TODO: Probably error out for cases with parameters?
        def bs = matchBlockStatement(st);
        if (bs != null) {
            ModelASTNamedArgumentList groovyBlock = new ModelASTNamedArgumentList(bs.body)
            ModelASTKey key = new ModelASTKey(null)
            key.key = "scriptBlock"
            groovyBlock.arguments.put(key, ModelASTValue.fromConstant(getSourceText(bs.body.code), bs.body.code))
            scriptBlock.args = groovyBlock
        } else {
            errorCollector.error(scriptBlock, Messages.ModelParser_StepWithoutBlock(pronoun))
        }

        return scriptBlock
    }

    /**
     * Parses a statement into a {@link ModelASTAgent}
     */
    public @Nonnull ModelASTAgent parseAgent(Statement st) {
        ModelASTAgent agent = new ModelASTAgent(st)
        def m = matchBlockStatement(st);
        def mc = matchMethodCall(st);
        if (m==null) {
            if (mc == null) {
                // Not sure of a better way to deal with this - it's a full-on parse-time failure.
                errorCollector.error(agent, Messages.ModelParser_ExpectedAgent())
            } else {
                List<Expression> args = ((TupleExpression) mc.arguments).expressions
                if (args.isEmpty()) {
                    errorCollector.error(agent, Messages.ModelParser_NoArgForAgent())
                } else if (args.size() > 1) {
                    errorCollector.error(agent, Messages.ModelParser_InvalidAgent())
                } else {
                    def agentCode = parseKey(args[0])
                    if (!(agentCode.key in DeclarativeAgentDescriptor.zeroArgModels().keySet())) {
                        errorCollector.error(agent, Messages.ModelParser_InvalidAgent())
                    } else {
                        agent.agentType = agentCode
                    }
                }
            }
        } else {
            def block = asBlock(m.body.code)
            if (block.statements.isEmpty()) {
                errorCollector.error(agent, Messages.ModelParser_ExpectedAgent())
            } else if (block.statements.size() > 1) {
                errorCollector.error(agent, Messages.ModelParser_OneAgentMax())
            } else {
                def typeMeth = matchMethodCall(block.statements[0])
                if (typeMeth == null) {
                    errorCollector.error(agent, Messages.ModelParser_ExpectedAgent())
                } else {
                    agent.agentType = parseKey(typeMeth.method)
                    ModelASTClosureMap parsed = parseClosureMap(m.body)
                    agent.variables = parsed.variables.get(agent.agentType)
                }
            }
        }

        return agent
    }

    public @Nonnull ModelASTPostBuild parsePostBuild(Statement stmt) {
        def r = new ModelASTPostBuild(stmt);

        return parseBuildConditionResponder(stmt, r);
    }

    public @Nonnull ModelASTPostStage parsePostStage(Statement stmt) {
        def r = new ModelASTPostStage(stmt);

        return parseBuildConditionResponder(stmt, r);
    }

    @Nonnull
    public <R extends ModelASTBuildConditionsContainer> R parseBuildConditionResponder(Statement stmt, R responder) {
        def m = matchBlockStatement(stmt);

        if (m==null) {
            errorCollector.error(responder, Messages.ModelParser_ExpectedBlock());
        } else {
            eachStatement(m.body.code) {
                ModelASTBuildCondition bc = parseBuildCondition(it)
                if (bc.condition != null && bc.branch != null) {
                    responder.conditions.add(bc);
                }
            }
        }
        return responder;
    }

    public @Nonnull ModelASTBuildCondition parseBuildCondition(Statement st) {
        ModelASTBuildCondition b = new ModelASTBuildCondition(st)
        def m = matchBlockStatement(st);
        if (m == null) {
            errorCollector.error(b, Messages.ModelParser_InvalidBuildCondition(BuildCondition.getOrderedConditionNames()))
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
            if (e.name in DeclarativeAgentDescriptor.zeroArgModels().keySet()) {
                return ModelASTValue.fromConstant(e.name, e)
            }
        }

        // for other composite expressions, treat it as in-place GString
        return ModelASTValue.fromGString("\${"+getSourceText(e)+"}", e)
    }

    protected String parseStringLiteral(Expression exp) {
        def s = matchStringLiteral(exp)
        if (s==null) {
            errorCollector.error(ModelASTValue.fromConstant(null, exp), Messages.ModelParser_ExpectedStringLiteral())
        }
        return s?:"error";
    }

    protected @CheckForNull String matchStringLiteral(Expression exp) {
        if (exp instanceof ConstantExpression) {
            return castOrNull(String,exp.value);
        } else if (exp instanceof VariableExpression) {
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
            errorCollector.error(ModelASTValue.fromConstant(e.getValue(), e),
                Messages.ModelParser_ExpectedStringLiteralButGot(e.value))
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
            errorCollector.error(ModelASTValue.fromConstant(null, exp), Messages.ModelParser_ExpectedSymbol())
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
                Boolean failFast = null
                Map<String,ClosureExpression> parallelArgs = new LinkedHashMap<>()
                if (sz==1) {
                    def branches = castOrNull(NamedArgumentListExpression, args.getExpression(sz - 1));
                    if (branches!=null) {
                        for (MapEntryExpression e : branches.mapEntryExpressions) {
                            String keyName = matchStringLiteral(e.keyExpression)
                            if (keyName != null && keyName.equals("failFast")) {
                                ConstantExpression exp = castOrNull(ConstantExpression.class, e.valueExpression)
                                if (exp == null || !(exp.value instanceof Boolean)) {
                                    errorCollector.error(new ModelASTKey(e.keyExpression),
                                        Messages.ModelParser_ExpectedFailFast())
                                } else {
                                    failFast = exp.value
                                }
                            } else {
                                ClosureExpression value = castOrNull(ClosureExpression, e.valueExpression);
                                if (value == null) {
                                    errorCollector.error(new ModelASTKey(e.keyExpression),
                                        Messages.ModelParser_ExpectedClosureOrFailFast())
                                } else {
                                    parallelArgs[parseStringLiteral(e.keyExpression)] = value;
                                }
                            }
                        }
                    }
                }
                return new ParallelMatch(whole, parallelArgs, failFast);
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

    protected List<String> methodNamesFromBlock(BlockStatement block) {
        return block.statements.collect { s ->
            def mc = matchMethodCall(s);
            if (mc != null) {
                return matchMethodName(mc);
            } else {
                return null
            }
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
        int beginLine = n.getLineNumber()
        int endLine = n.getLastLineNumber()
        int beginLineColumn = n.getColumnNumber()
        int endLineLastColumn = n.getLastColumnNumber()

        //The node seems to be lying about the last line, so go through each statement to try to make sure
        if (n instanceof BlockStatement) {
            for (Statement s : n.statements) {
                if (s.lineNumber < beginLine) {
                    beginLine = s.lineNumber
                    beginLineColumn = s.columnNumber
                }
                if (s.lastLineNumber > endLine) {
                    endLine = s.lastLineNumber
                    endLineLastColumn = s.lastColumnNumber
                }
            }
        }
        for (int x = beginLine; x <= endLine; x++) {
            String line = sourceUnit.source.getLine(x, null);
            if (line == null)
                throw new AssertionError("Unable to get source line"+x);

            if (x == endLine) {
                line = line.substring(0, endLineLastColumn - 1);
            }
            if (x == beginLine) {
                line = line.substring(beginLineColumn - 1);
            }
            result.append(line).append('\n');
        }

        return result.toString().trim();
    }
}
