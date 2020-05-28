package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import net.sf.json.JSONArray;
import org.jenkinsci.plugins.pipeline.modeldefinition.validator.ModelValidator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 *
 * @author Liam Newman
 */
public class ModelASTAxisContainer extends ModelASTElement {

    private List<ModelASTAxis> axes = new ArrayList<>();

    public ModelASTAxisContainer(Object sourceLocation) {
        super(sourceLocation);
    }

    @Override
    @Nonnull
    public JSONArray toJSON() {
        return toJSONArray(axes);
    }

    @Override
    public void validate(@Nonnull ModelValidator validator) {
        validator.validateElement(this);
        validate(validator, axes);
    }

    @Override
    @Nonnull
    public String toGroovy() {
        return toGroovyBlock("axes", axes);
    }

    @Override
    public void removeSourceLocation() {
        super.removeSourceLocation();
        removeSourceLocationsFrom(axes);
    }

    @Override
    public String toString() {
        return "ModelASTAxis{" +
                "axes=" + axes +
                "}";
    }

    public List<ModelASTAxis> getAxes() {
        return axes;
    }

    public void setAxes(List<ModelASTAxis> axes) {
        this.axes = axes;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModelASTAxisContainer)) return false;
        if (!super.equals(o)) return false;
        ModelASTAxisContainer that = (ModelASTAxisContainer) o;
        return Objects.equals(getAxes(), that.getAxes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getAxes());
    }
}
