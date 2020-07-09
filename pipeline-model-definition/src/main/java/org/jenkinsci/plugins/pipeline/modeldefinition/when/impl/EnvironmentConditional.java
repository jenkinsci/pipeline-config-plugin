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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.Comparator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * Stage condition based on environment variable equality.
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public class EnvironmentConditional extends DeclarativeStageConditional<EnvironmentConditional> {
    private final String name;
    private final String value;
    private boolean ignoreCase = false;
    private String comparator;

    @DataBoundConstructor
    public EnvironmentConditional(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @DataBoundSetter
    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    /**
     * The {@link Comparator} to use.
     * Default is {@link Comparator#EQUALS}
     * @return the name of the comparator or null if default.
     */
    public String getComparator() {
        return comparator;
    }

    @DataBoundSetter
    public void setComparator(String comparator) {
        final Comparator c = Comparator.get(comparator, null);
        if (c != null) {
            this.comparator = c.name();
        } else {
            this.comparator = null;
        }
    }

    public boolean environmentMatches(String v, String var) {
        Comparator c = Comparator.get(comparator, Comparator.EQUALS);
        return c.compare(v, var, !this.isIgnoreCase());
    }

    @Extension
    @Symbol("environment")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<EnvironmentConditional> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute the stage if an environment variable exists and matches a pattern";
        }

        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }
    }
}
