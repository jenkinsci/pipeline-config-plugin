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

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import jenkins.model.ArtifactManager;
import jenkins.model.Jenkins;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.ast.expr.Expression;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;
import org.jenkinsci.plugins.pipeline.modeldefinition.parser.ASTParserUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.utils.Comparator;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class StashedConditional extends DeclarativeStageConditional<StashedConditional> {

    private final String name;
    private String path;
    private String content;
    private String comparator;

    @DataBoundConstructor
    public StashedConditional(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @CheckForNull
    public String getPath() {
        return path;
    }

    @DataBoundSetter
    public void setPath(String path) {
        this.path = Util.fixEmptyAndTrim(path);
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

        StashManager.StashAwareArtifactManager stashAwareArtifactManager = null;
        try {
            stashAwareArtifactManager = stashAwareArtifactManager(run);
        } catch (IOException e) {
            script.println("Failed to pick artifact manager: " + e.getMessage());
        }
        if (stashAwareArtifactManager != null) {
            //Need to take the slow way because lack of list API
            File tempFile = null;
            try {
                tempFile = Files.createTempDirectory("pipeline-when-stashed").toFile();
                tempFile.deleteOnExit();
                FilePath tmpWorkspace = new FilePath(tempFile);
                final Computer jc = Jenkins.get().toComputer();
                EnvVars env = null;
                if (jc != null) {
                    env = jc.getEnvironment();
                } else {
                    env = new EnvVars();
                }
                stashAwareArtifactManager.unstash(name,
                        tmpWorkspace,
                        Jenkins.get().createLauncher(TaskListener.NULL),
                        env,
                        TaskListener.NULL);
                if (tmpWorkspace.list().isEmpty()) {
                    return false;
                }
                if (path != null) {
                    final FilePath[] list = tmpWorkspace.list(path);
                    if (content != null) {
                        for (FilePath f : list) {
                            //TODO limit content size?
                            if (Comparator.get(comparator, Comparator.EQUALS).compare(content, f.readToString().trim())) {
                                return true;
                            }
                        }
                        return false;
                    } else {
                        return list.length > 0;
                    }
                }
                return true;

            } catch(AbortException ae) {
                //See org.jenkinsci.plugins.workflow.flow.StashManager.unstash
                return false;
            } catch (Exception e) {
                script.println("Failed to extract content of stash: " + e.getMessage());
                return false;
            }  finally {
                if (tempFile != null) {
                    FileUtils.deleteQuietly(tempFile);
                }
            }
        } else {
            //conventional stash storage (this is how StashManager does it but no real API to use)
            File stash = new File(new File(run.getRootDir(), "stashes"), name + SUFFIX);
            if (!stash.exists() || !stash.isFile()) {
                return false;
            }
            if (path != null) {
                FilePath fp = new FilePath(stash);
                try (TarArchiveInputStream t = new TarArchiveInputStream(FilePath.TarCompression.GZIP.extract(fp.read()))) {
                    TarArchiveEntry te;
                    while ((te = t.getNextTarEntry()) != null) {
                        if (Comparator.GLOB.compare(path, te.getName())) {
                            if (content != null && te.isFile()) {
                                //TODO limit content size?
                                String s = IOUtils.toString(t, "UTF-8");
                                if (s != null) {
                                    s = s.trim();
                                }
                                if (Comparator.get(comparator, Comparator.EQUALS).compare(content, s)) {
                                    return true;
                                }
                            } else if (content == null) {
                                return true;
                            }
                        }
                    }
                    return false;
                } catch (Exception e) {
                    script.println("Failed to extract content of stash: " + e.getMessage());
                }
            }
            return true;
        }
    }

    private static @CheckForNull
    StashManager.StashAwareArtifactManager stashAwareArtifactManager(@Nonnull Run<?, ?> build) throws IOException {
        ArtifactManager am = build.pickArtifactManager();
        return am instanceof StashManager.StashAwareArtifactManager ? (StashManager.StashAwareArtifactManager) am : null;
    }

    private static final String SUFFIX = ".tar.gz";

    @Extension
    @Symbol("stashed")
    public static class DescriptorImpl extends DeclarativeStageConditionalDescriptor<StashedConditional> {

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute the stage if a previous stage stashed something with the given conditions";
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
