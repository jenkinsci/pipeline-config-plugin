package org.jenkinsci.plugins.pipeline.modeldefinition.ast;

/**
 * A marker for classes that can serve as the argument for a method, either as part of a {@link
 * ModelASTKeyValueOrMethodCallPair} or on its own in a list.
 *
 * @author Andrew Bayer
 */
public interface ModelASTMethodArg extends ModelASTMarkerInterface {}
