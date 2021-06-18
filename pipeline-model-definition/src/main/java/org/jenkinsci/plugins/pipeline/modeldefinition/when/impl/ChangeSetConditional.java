/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import hudson.scm.ChangeLogSet;
import hudson.util.ListBoxModel;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.Comparator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * Conditional that checks the affected file paths recorded in the changelog.
 *
 * The build must first have collected the changelog via for example <code>checkout scm</code>.
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID")
public class ChangeSetConditional extends DeclarativeStageConditional<ChangeSetConditional> {

    @Deprecated
    private transient String glob;
    private String pattern;
    private boolean caseSensitive;
    private boolean shouldMatchAll;
    private String comparator;

    @DataBoundConstructor
    public ChangeSetConditional(String pattern) {
        this.pattern = pattern;
        this.caseSensitive = false;
        this.shouldMatchAll = false;
    }

    @Deprecated
    public String getGlob() {
        return glob;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isShouldMatchAll() {
        return shouldMatchAll;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * The {@link Comparator} to use.
     * Default is {@link Comparator#GLOB}
     * @return the name of the comparator or null if default.
     */
    public String getComparator() {
        return comparator;
    }

    protected Object readResolve() throws IOException {
        if (this.glob != null) {
            this.pattern = this.glob;
        }
        return this;
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

    @DataBoundSetter
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @DataBoundSetter
    public void setShouldMatchAll(boolean shouldMatchAll) {
        this.shouldMatchAll = shouldMatchAll;
    }

    public boolean changeSetMatches(ChangeLogSet.Entry change, String pattern, boolean caseSensitive, boolean shouldMatchAll) {
        Comparator c = Comparator.get(comparator, Comparator.GLOB);
        if (shouldMatchAll) {
            return change.getAffectedPaths().stream().allMatch(path -> c.compare(pattern, path, caseSensitive));
        } else {
            return change.getAffectedPaths().stream().anyMatch(path -> c.compare(pattern, path, caseSensitive));
        }
    }

    @Deprecated
    public boolean changeSetMatches(ChangeLogSet.Entry change, String pattern, boolean caseSensitive) {
        return changeSetMatches(change, pattern, caseSensitive, this.isShouldMatchAll());
    }

    @Extension
    @Symbol("changeset")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<ChangeSetConditional> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Execute the stage if the changeset contains a file matching a pattern";
        }

        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }

        public ListBoxModel doFillComparatorItems() {
            return Comparator.getSelectOptions(true, Comparator.GLOB);
        }
    }
}
