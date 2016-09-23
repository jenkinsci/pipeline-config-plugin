package org.jenkinsci.plugins.pipeline.modeldefinition.ast

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import net.sf.json.JSONArray
import net.sf.json.JSONObject
import org.jenkinsci.plugins.pipeline.modeldefinition.model.Stage
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator

/**
 * Represents an individual {@link Stage} and the {@link ModelASTBranch}s it may contain.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 * @see ModelASTPipelineDef
 */
@ToString(includeSuper = true, includeSuperProperties = true)
@EqualsAndHashCode(callSuper = true)
@SuppressFBWarnings(value="SE_NO_SERIALVERSIONID")
public final class ModelASTStage extends ModelASTElement {
    public static final List<String> possibleKeys = ["config", "steps"]

    String name
    ModelASTStageConfig config
    List<ModelASTBranch> branches = []

    public ModelASTStage(Object sourceLocation) {
        super(sourceLocation)
    }

    @Override
    public JSONObject toJSON() {
        JSONArray a = new JSONArray()
        branches.each { br ->
            a.add(br.toJSON())
        }
        JSONObject o = new JSONObject()
        o.accumulate("name",name)
        o.accumulate("branches",a)
        if (config != null) {
            o.accumulate("config", config.toJSON())
        }
        return o
    }

    @Override
    public void validate(ModelValidator validator) {
        validator.validateElement(this)
        branches.each { b ->
            b?.validate(validator)
        }
        config?.validate(validator)
    }

    @Override
    public String toGroovy() {
        StringBuilder retString = new StringBuilder()
        retString.append("stage(\"${name}\") {\n")
        if (config != null) {
            retString.append(config.toGroovy())
            retString.append("steps {\n")
        }
        if (branches.size() > 1) {
            retString.append("parallel(\n")
            List<String> branchStrings = branches.collect { b ->
                "${b.name}: {\n${b.toGroovy()}\n}"
            }
            retString.append(branchStrings.join(",\n"))
            retString.append(")\n")
        } else if (branches.size() == 1) {
            retString.append(branches.get(0).toGroovy())
        }

        if (config != null) {
            retString.append("}\n")
        }
        retString.append("}\n")

        return retString.toString()
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation()
        branches.each { b ->
            b.removeSourceLocation()
        }
        config?.removeSourceLocation()
    }
}
