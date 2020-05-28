package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;

/**
 * Represents the collection of {@code Stage}s to be executed in the build in parallel. Corresponds to {@code Stages}.
 * Used as a base to hold common functionality between parallel and matrix.
 *
 * @author Liam Newman
 */
public class ModelASTParallel extends ModelASTStages {

    public ModelASTParallel(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    public void validate(@Nonnull final ModelValidator validator) {
        validate(validator, true);
    }

    @Override
    public void validate(final ModelValidator validator, boolean isWithinParallel) {
        super.validate(validator, true);
        validator.validateElement(this);
    }

    @Override
    @Nonnull
    public String toGroovy() {
        return toGroovyBlock("parallel", getStages());
    }

    @Override
    public String toString() {
        return "ModelASTParallel{" +
                "stages=" + getStages() +
                "}";
    }
}
