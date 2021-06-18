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
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.DirScanner;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerConfiguration;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.StandardArtifactManager;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.workflow.flow.StashManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

/**
 * Tests {@link StashedConditional}
 */
public class StashedConditionalTest extends AbstractModelDefTest {

    @Test
    public void happy() throws Exception {
        assumeThat(Functions.isWindows(), equalTo(false));
        doHappy();
        assertFalse(managed);
    }

    @Test
    public void happyArtifactManager() throws Exception {
        assumeThat(Functions.isWindows(), equalTo(false));
        doHappy();
        assertTrue(managed);
    }

    private void doHappy() throws Exception {
        ExpectationsBuilder expect = expect("when/conditions/stashed", "stashed").runFromRepo(false);
        expect.logContains(
                "One", "Hello",
                "Two", "Dull World",
                "Three", "We have a number",
                "Four")
                .logNotContains("Good day Sir!").go();
    }

    @Test
    public void manyFiles() throws Exception {
        assumeThat(Functions.isWindows(), equalTo(false));
        doManyFiles();
        assertFalse(managed);
    }

    @Test
    public void manyFilesArtifactManager() throws Exception {
        assumeThat(Functions.isWindows(), equalTo(false));
        doManyFiles();
        assertTrue(managed);
    }

    private void doManyFiles() throws Exception {
        ExpectationsBuilder expect = expect("when/conditions/stashed", "manyfiles").runFromRepo(false);
        expect.logContains(
                "One", "Hello",
                "Two", "Dull World",
                "Three", "We have a greeting").go();
    }



    @Test
    public void manyFilesNoMatch() throws Exception {
        assumeThat(Functions.isWindows(), equalTo(false));
        doManyFilesNoMatch();
        assertFalse(managed);
    }

    @Test
    public void manyFilesNoMatchArtifactManager() throws Exception {
        assumeThat(Functions.isWindows(), equalTo(false));
        doManyFilesNoMatch();
        assertTrue(managed);
    }

    private void doManyFilesNoMatch() throws Exception {
        ExpectationsBuilder expect = expect("when/conditions/stashed", "manyfilesnomatch").runFromRepo(false);
        expect.logContains(
                "One", "Hello",
                "Two", "Dull World",
                "Three")
                .logNotContains("We have a greeting").go();
    }

    @Before
    public void setArtifactManager() {
        managed = false;
        if (testName.getMethodName().endsWith("ArtifactManager")) {
            ArtifactManagerConfiguration.get().getArtifactManagerFactories().add(new MockArtifactManagerFactory());
        }
    }

    @After
    public void removeArtifactManagers() throws IOException {
        ArtifactManagerConfiguration.get().getArtifactManagerFactories().remove(MockArtifactManagerFactory.class);
    }

    @Rule
    public TestName testName = new TestName();

    public static volatile boolean managed = false;

    public static class MockArtifactManagerFactory extends ArtifactManagerFactory {

        @DataBoundConstructor
        public MockArtifactManagerFactory() {
        }

        @CheckForNull
        @Override
        public ArtifactManager managerFor(Run<?, ?> build) {
            managed = true;
            return new StandardStashAwareArtifactManager(build);
        }

        @TestExtension({"testHappyArtifactManager", "manyFilesArtifactManager", "manyFilesNoMatchArtifactManager"})
        public static class DescriptorImpl extends ArtifactManagerFactoryDescriptor {
            @Nonnull
            @Override
            public String getDisplayName() {
                return "Mock Archiver";
            }

            public static DescriptorImpl get() {
                return ExtensionList.lookupSingleton(DescriptorImpl.class);
            }
        }
    }

    static class StandardStashAwareArtifactManager extends StandardArtifactManager implements StashManager.StashAwareArtifactManager {

        public StandardStashAwareArtifactManager(Run<?, ?> build) {
            super(build);
        }

        @Override
        public void stash(@Nonnull String name, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull EnvVars env, @Nonnull TaskListener listener, @CheckForNull String includes, @CheckForNull String excludes, boolean useDefaultExcludes, boolean allowEmpty) throws IOException, InterruptedException {
            File f = new File(this.build.getRootDir(), "mockstashes");
            f.mkdirs();
            final FilePath path = new FilePath(f).child(name + ".tar");
            DirScanner scanner = new DirScanner.Glob(includes, excludes, useDefaultExcludes);
            workspace.tar(path.write(), scanner);
        }

        @Override
        public void unstash(@Nonnull String name, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull EnvVars env, @Nonnull TaskListener listener) throws IOException, InterruptedException {
            File f = new File(this.build.getRootDir(), "mockstashes");
            final FilePath path = new FilePath(f).child(name + ".tar");
            if (!path.exists()) {
                throw new AbortException("No stash with name " + name);
            }
            path.untar(workspace, FilePath.TarCompression.NONE);
        }

        @Override
        public void clearAllStashes(@Nonnull TaskListener listener) throws IOException, InterruptedException {
            File f = new File(this.build.getRootDir(), "mockstashes");
            FileUtils.deleteQuietly(f);
        }

        @Override
        public void copyAllArtifactsAndStashes(@Nonnull Run<?, ?> to, @Nonnull TaskListener listener) throws IOException, InterruptedException {
            throw new AbortException("Don't want to");
        }
    }
}
