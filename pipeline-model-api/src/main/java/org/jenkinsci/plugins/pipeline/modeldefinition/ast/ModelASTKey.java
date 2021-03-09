package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents the key in a key/value pair, as used in {@link ModelASTEnvironment}, {@link
 * ModelASTNamedArgumentList} and elsewhere.
 *
 * @author Andrew Bayer
 */
public class ModelASTKey extends ModelASTElement {
  private String key;

  public ModelASTKey(Object sourceLocation) {
    super(sourceLocation);
  }

  @Override
  @NonNull
  public Object toJSON() {
    return key;
  }

  @Override
  @NonNull
  public String toGroovy() {
    return key;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  @Override
  public String toString() {
    return "ModelASTKey{" + "key='" + key + '\'' + "}";
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

    ModelASTKey that = (ModelASTKey) o;

    return getKey() != null ? getKey().equals(that.getKey()) : that.getKey() == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (getKey() != null ? getKey().hashCode() : 0);
    return result;
  }
}
