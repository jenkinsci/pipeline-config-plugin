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

package org.jenkinsci.plugins.pipeline.modeldefinition

import com.google.common.base.Predicate
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import hudson.ExtensionList
import hudson.model.Describable
import hudson.model.Descriptor
import org.apache.commons.codec.digest.DigestUtils
import org.jenkinsci.plugins.pipeline.StageStatus
import org.jenkinsci.plugins.pipeline.StageTagsMetadata
import org.jenkinsci.plugins.pipeline.SyntheticStage
import org.jenkinsci.plugins.pipeline.modeldefinition.actions.ExecutionModelAction
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTPipelineDef
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStages
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodsToList
import org.jenkinsci.plugins.pipeline.modeldefinition.model.StepsBlock
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.Converter
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor
import org.jenkinsci.plugins.structs.SymbolLookup
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.structs.describable.DescribableParameter
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.actions.TagsAction
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.cps.CpsThread
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode
import org.jenkinsci.plugins.workflow.graph.FlowNode
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.steps.StepDescriptor
import org.jenkinsci.plugins.workflow.support.steps.StageStep
import org.jvnet.tiger_types.Types

import javax.annotation.Nullable
import javax.lang.model.SourceVersion
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

// TODO: Prune like mad once we have step-in-groovy and don't need these static whitelisted wrapper methods.
/**
 * Utility methods for use primarily in CPS-transformed code to avoid excessive global whitelisting.
 *
 * @author Andrew Bayer
 */
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public class Utils {

    /**
     * Workaround for not having to whitelist isAssignableFrom, metaClass etc to determine whether a field on
     * a class is of a specific type.
     *
     * @param fieldType The type we're checking
     * @param actualClass The class we're inspecting
     * @param fieldName The name of the field - could be a singular when the field name is plural, in which case
     *   we'll get the actual name from actualFieldName(...)
     * @return True if the field exists and is of the given type.
     */
    public static boolean isFieldA(Class fieldType, Class actualClass, String fieldName) {
        def actualFieldName = actualFieldName(actualClass, fieldName)
        def realFieldType = actualClass.metaClass.getMetaProperty(actualFieldName)?.type

        if (realFieldType == null) {
            return false
        } else {
            return realFieldType == fieldType || fieldType.isAssignableFrom(realFieldType)
        }
    }

    /**
     * Gets the actual field name for a possibly-needs-to-be-pluralized name.
     *
     * @param actualClass The class we're inspecting
     * @param fieldName The original field name, which could need to be pluralized.
     * @return The real field name, pluralized if necessary, or null if not found.
     */
    public static String actualFieldName(Class actualClass, String fieldName) {
        if (actualClass.metaClass.getMetaProperty(fieldName) != null) {
            return fieldName
        } else if (actualClass.metaClass.getMetaProperty("${fieldName}s") != null) {
            return "${fieldName}s"
        } else {
            return null
        }
    }

    /**
     * Get the actual field type or contained field type in the case of parameterized types in the inspected class.
     *
     * @param actualClass The class we're inspecting
     * @param fieldName The field name we're looking for, which could get pluralized.
     * @return The class of the field in the inspected class, or the class contained in the list or map.
     */
    public static Class actualFieldType(Class actualClass, String fieldName) {
        def actualFieldName = actualFieldName(actualClass, fieldName)
        if (actualFieldName == null) {
            return null
        } else {
            def field = actualClass.getDeclaredFields().find { !it.isSynthetic() && it.name == actualFieldName }
            // If the field's a ParameterizedType, we need to check it to see if it's containing a Plumber class.
            if (field.getGenericType() instanceof ParameterizedType) {
                if (Map.class.isAssignableFrom(field.getType())) {
                    return (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[1]
                } else {
                    // First class listed in the actual type arguments - we ignore anything past this because eh.
                    return (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]
                }
            } else {
                return field.getType()
            }
        }

    }

    /**
     * Finds the parameterized type argument for a {@link MethodsToList} class and returns it.
     *
     * @param c A class.
     * @return The parameterized type argument for the class, if it's a {@link MethodsToList} class. Null otherwise.
     */
    public static Class<Describable> getMethodsToListType(Class c) {
        Class retClass
        c.genericInterfaces.each { Type t ->
            if (t instanceof ParameterizedType) {
                if (t.getRawType() instanceof Class
                    && MethodsToList.class.isAssignableFrom((Class)t.getRawType())
                    && t.getActualTypeArguments().first() instanceof Class) {
                    retClass = (Class)t.actualTypeArguments.first()
                }
            }
        }

        return retClass
    }

    /**
     * Simple wrapper for isInstance to avoid whitelisting issues.
     *
     * @param c The class to check against
     * @param o The object to check
     * @return True if the object is an instance of the class, false otherwise
     */
    public static boolean instanceOfWrapper(Class c, Object o) {
        return c.isInstance(o)
    }

    /**
     * Simple wrapper for isAssignableFrom to avoid whitelisting issues.
     *
     * @param c The class that should be assignable from
     * @param o The class to check
     * @return True if o can be assigned to c, false otherwise
     */
    public static boolean assignableFromWrapper(Class c, Class o) {
        return c.isAssignableFrom(o)
    }

    public static boolean hasScmContext(CpsScript script) {
        try {
            // Just rely on SCMVar's own context-checking (via CpsScript) rather than brewing our own.
            script.getProperty("scm")
            return true
        } catch (_) {
            // If we get an IllegalStateException, "checkout scm" isn't valid, so return false.
            return false
        }
    }

    static void attachDeclarativeActions(CpsScript script) {
        WorkflowRun r = script.$build()
        ModelASTPipelineDef model = Converter.parseFromCpsScript(script)

        ModelASTStages stages = model.stages

        stages.removeSourceLocation()
        if (r.getAction(SyntheticStageGraphListener.GraphListenerAction.class) == null) {
            r.addAction(new SyntheticStageGraphListener.GraphListenerAction())
        }
        if (r.getAction(ExecutionModelAction.class) == null) {
            r.addAction(new ExecutionModelAction(stages))
        }
    }

    static Predicate<FlowNode> endNodeForStage(final StepStartNode startNode) {
        return new Predicate<FlowNode>() {
            @Override
            boolean apply(@Nullable FlowNode input) {
                return input != null &&
                    input instanceof StepEndNode &&
                    input.getStartNode().equals(startNode)
            }
        }
    }

    static Predicate<FlowNode> isStageWithOptionalName(final String stageName = null) {
        return new Predicate<FlowNode>() {
            @Override
            boolean apply(@Nullable FlowNode input) {
                return input != null &&
                    input instanceof StepStartNode &&
                    ((StepStartNode) input).descriptor instanceof StageStep.DescriptorImpl &&
                    (stageName == null || input.displayName?.equals(stageName))
            }
        }
    }

    public static String stringToSHA1(String s) {
        return DigestUtils.sha1Hex(s)
    }

    /**
     * Prints a log message to the Jenkins log, bypassing the echo step.
     * @param s The message to log
     */
    public static void logToTaskListener(String s) {
        CpsThread thread = CpsThread.current()
        CpsFlowExecution execution = thread.execution

        execution?.getOwner()?.getListener()?.getLogger()?.println(s)
    }

    /**
     * Returns true if we're currently nested under a stage.
     *
     * @return true if we're in a stage and false otherwise
     */
    static boolean withinAStage() {
        CpsThread thread = CpsThread.current()
        CpsFlowExecution execution = thread.execution

        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();

        FlowNode stageNode = execution.currentHeads.find { h ->
            scanner.findFirstMatch(h, isStageWithOptionalName())
        }

        return stageNode != null
    }

    private static FlowNode findStageFlowNode(String stageName) {
        CpsThread thread = CpsThread.current()
        CpsFlowExecution execution = thread.execution

        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();

        return scanner.findFirstMatch(execution.currentHeads, null, isStageWithOptionalName(stageName))
    }

    private static void markStageWithTag(String stageName, String tagName, String tagValue) {
        FlowNode currentNode = findStageFlowNode(stageName)

        if (currentNode != null) {
            TagsAction tagsAction = currentNode.getAction(TagsAction.class)
            if (tagsAction == null) {
                tagsAction = new TagsAction()
                tagsAction.addTag(tagName, tagValue)
                currentNode.addAction(tagsAction)
            } else if (tagsAction.getTagValue(tagName) == null) {
                tagsAction.addTag(tagName, tagValue)
                currentNode.save()
            }
        }
    }

    static <T extends StageTagsMetadata> T getTagMetadata(Class<T> c) {
        return ExtensionList.lookup(StageTagsMetadata.class).get(c)
    }

    static StageStatus getStageStatusMetadata() {
        return getTagMetadata(StageStatus.class)
    }

    static SyntheticStage getSyntheticStageMetadata() {
        return getTagMetadata(SyntheticStage.class)
    }

    static void markStageFailedAndContinued(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().failedAndContinued)
    }

    static void markStageSkippedForFailure(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().skippedForFailure)
    }

    static void markStageSkippedForUnstable(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().skippedForFailure)
    }

    static void markStageSkippedForConditional(String stageName) {
        markStageWithTag(stageName, getStageStatusMetadata().tagName, getStageStatusMetadata().skippedForConditional)
    }

    /**
     * Creates and sets the loading for a cache of {@link Describable}s descending from the given descriptor type.
     *
     * @param type The {@link Descriptor} class whose extensions we want to find.
     * @param includeClassNames Optionally include class names as keys. Defaults to false.
     * @param excludedSymbols Optional list of symbol names to exclude from the cache.
     * @return A {@link LoadingCache} for looking up types from symbols.
     */
    static generateTypeCache(Class<? extends Descriptor> type, boolean includeClassNames = false,
                             List<String> excludedSymbols = [], Closure<Boolean> filter = null) {
        return CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<Object, Map<String, String>>() {
            @Override
            Map<String, String> load(Object key) throws Exception {
                return populateTypeCache(type, includeClassNames, excludedSymbols, filter)
            }
        })
    }

    /**
     * Actually populates the type cache.
     *
     * @param type The {@link Descriptor} class whose extensions we want to find.
     * @param includeClassNames Optionally include class names as keys. Defaults to false.
     * @param excludedSymbols Optional list of symbol names to exclude from the cache.
     * @return A map of symbols or class names to class names.
     */
    private static Map<String,String> populateTypeCache(Class<? extends Descriptor> type,
                                                        boolean includeClassNames = false,
                                                        List<String> excludedSymbols = [],
                                                        Closure<Boolean> filter = null) {
        Map<String,String> knownTypes = [:]

        ExtensionList.lookup(type).each { t ->
            if (filter == null || filter.call(t)) {
                // Have to special-case StepDescriptor since it doesn't actually have symbols!
                if (t instanceof StepDescriptor) {
                    knownTypes.put(t.functionName, t.clazz.getName())
                } else {
                    Set<String> symbolValue = SymbolLookup.getSymbolValue(t)
                    if (!symbolValue.isEmpty() && !symbolValue.any { excludedSymbols.contains(it) }) {
                        knownTypes.put(symbolValue.iterator().next(), t.clazz.getName())
                    }
                }

                if (includeClassNames) {
                    // Add the class name mapping even if we also found the symbol, for backwards compatibility reasons.
                    knownTypes.put(t.clazz.getName(), t.clazz.getName())
                }
            }
        }

        return knownTypes
    }

    /**
     * Determines whether a given {@link UninstantiatedDescribable} is of a given type.
     *
     * @param ud The {@link UninstantiatedDescribable} to check
     * @param base The {@link Class}
     * @return True if the uninstantiated describable is of the type given
     */
    public static boolean isOfType(UninstantiatedDescribable ud, Class<?> base) {
        Descriptor d = SymbolLookup.get().findDescriptor(base, ud.symbol)
        return d != null
    }

    /**
     * @param c The closure to wrap.
     */
    public static StepsBlock createStepsBlock(Closure c) {
        // Jumping through weird hoops to get around the ejection for cases of JENKINS-26481.
        StepsBlock wrapper = new StepsBlock()
        wrapper.setClosure(c)

        return wrapper
    }

    public static boolean validEnvIdentifier(String i) {
        if (!SourceVersion.isIdentifier(i)) {
            return false
        } else if (!i.matches("[a-zA-Z_]+[a-zA-Z0-9_]*")) {
            return false
        }
        return true
    }

    public static boolean whenConditionDescriptorFound(String name) {
        Descriptor d = DescriptorLookupCache.publicCache.lookupFunction(name)
        return d != null && d instanceof DeclarativeStageConditionalDescriptor
    }

    public static boolean nestedWhenCondition(String name) {
        Descriptor d = DescriptorLookupCache.publicCache.lookupFunction(name)
        if (d != null && d instanceof DeclarativeStageConditionalDescriptor) {
            return ((DeclarativeStageConditionalDescriptor)d).containsNested()
        }
        return false
    }

    public static boolean takesWhenConditionList(String name) {
        DescribableModel<? extends Describable> model = DescriptorLookupCache.publicCache.modelForStepOrFunction(name)

        if (model != null && Types.isSubClassOf(model.type, DeclarativeStageConditional.class)) {
            DescribableParameter p = model.soleRequiredParameter
            if (p != null) {
                return Types.isSubClassOf(p.getRawType(), Collection.class)
            }
        }

        return false
    }
}
