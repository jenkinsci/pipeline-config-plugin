package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import java.util.Map;

/**
 * Either single value, or named args
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public abstract class ModelASTArgumentList extends ModelASTElement {
  public ModelASTArgumentList(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  public String toString() {
    return "ModelASTArgumentList{}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    return true;
  }

  public abstract Map<String, ?> argListToMap();
}
