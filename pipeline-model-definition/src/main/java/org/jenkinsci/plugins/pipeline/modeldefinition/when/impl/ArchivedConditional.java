/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;
import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.Comparator;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

public class ArchivedConditional extends DeclarativeStageConditional<ArchivedConditional> {

    private final String path;
    private String content;
    private String comparator;

    @DataBoundConstructor
    public ArchivedConditional(@Nonnull String path) {
        this.path = path;
    }

    @Nonnull
    public String getPath() {
        return path;
    }

    @CheckForNull
    public String getContent() {
        return content;
    }

    @DataBoundSetter
    public void setContent(@CheckForNull String content) {
        this.content = Util.fixEmptyAndTrim(content);
    }

    @CheckForNull
    public String getComparator() {
        return comparator;
    }

    @DataBoundSetter
    public void setComparator(@CheckForNull String comparator) {
        Comparator c = Comparator.get(comparator, null);
        //TODO validation
        if (c != null) {
            this.comparator = c.name();
        } else {
            this.comparator = null;
        }
    }

    public boolean matches(CpsScript script) {
        final Run<?, ?> run = script.$buildNoException();
        if (run == null) {
            return false;
        }
        try {
            final ArtifactManager artifactManager = run.pickArtifactManager();
            final VirtualFile root = artifactManager.root();
            final Collection<String> list = root.list(path, null, true);
            if (list.isEmpty()) {
                return false;
            }
            if (content != null) {
                Comparator comp = Comparator.get(comparator, Comparator.EQUALS);
                for (String f : list) {
                    final VirtualFile file = root.child(f);
                    if (file.exists() && file.isFile() && file.canRead()) {
                        try (InputStream in = file.open()){
                            String s = IOUtils.toString(in);
                            if (s != null) {
                                s = s.trim();
                                if (comp.compare(content, s)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                return false;
            }
            return true;
        } catch (IOException e) {
            script.println("Failed to read archive: " + e.getMessage());
            return false;
        }
    }

    @Extension
    @Symbol("archived")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<ArchivedConditional> {

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute the stage if a previous stage archived something with the given conditions";
        }

        @Override
        public Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent original) {
            return ASTParserUtils.transformWhenContentToRuntimeAST(original);
        }

        public ListBoxModel doFillComparatorItems() {
            return Comparator.getSelectOptions(true, Comparator.EQUALS);
        }
    }
}
