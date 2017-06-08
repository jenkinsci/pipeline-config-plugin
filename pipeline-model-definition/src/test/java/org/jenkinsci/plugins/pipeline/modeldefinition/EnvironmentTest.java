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
 */
package org.jenkinsci.plugins.pipeline.modeldefinition;

import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author Andrew Bayer
 */
public class EnvironmentTest extends AbstractModelDefTest {

    private static Slave s;

    @BeforeClass
    public static void setUpAgent() throws Exception {
        s = j.createOnlineSlave();
        s.setLabelString("some-label docker");
    }

    @Test
    public void simpleEnvironment() throws Exception {
        expect("simpleEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "_UNDERSCORE is VALID")
                .go();
    }

    @Issue("JENKINS-43143")
    @Test
    public void paramsInEnvironment() throws Exception {
        expect("paramsInEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "_UNDERSCORE is VALIDAValue")
                .go();
    }

    @Issue("JENKINS-43137")
    @Test
    public void multilineEnvironment() throws Exception {
        expect("multilineEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "MULTILINE is VALID\n\"SO THERE\"")
                .go();
    }

    @Issue("JENKINS-42771")
    @Test
    public void multiExpressionEnvironment() throws Exception {
        expect("multiExpressionEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "_UNDERSCORE is VALID")
                .go();
    }

    @Test
    public void environmentInStage() throws Exception {
        expect("environmentInStage")
                .logContains("[Pipeline] { (foo)", "FOO is BAR")
                .go();
    }

    @Issue("JENKINS-41748")
    @Test
    public void environmentCrossReferences() throws Exception {
        expect("environmentCrossReferences")
                .logContains("[Pipeline] { (foo)",
                        "FOO is FOO",
                        "BAR is FOOBAR",
                        "BAZ is FOOBAZ",
                        "SPLODE is banana")
                .go();
    }

    @Issue("JENKINS-43872")
    @Test
    public void envDollarQuotes() throws Exception {
        expect("envDollarQuotes")
                .logContains("[Pipeline] { (foo)",
                        "FOO is ${FOOTHAT}",
                        "BAR is ${FOOTHAT}BAR",
                        "BAZ is ${FOOTHAT}BAZ",
                        "SPLODE is banana")
                .go();
    }

    @Test
    public void envDotCrossRef() throws Exception {
        expect("envDotCrossRef")
                .logContains("[Pipeline] { (foo)",
                        "MICROSERVICE_NAME is directory",
                        "IMAGE_NAME is quay.io/svc/directory",
                        "IMAGE_ID is quay.io/svc/directory:master_1",
                        "TAG_NAME is master_1")
                .go();
    }

    @Issue("JENKINS-43404")
    @Test
    public void envQuotesInQuotes() throws Exception {
        expect("envQuotesInQuotes")
                .logContains("[Pipeline] { (foo)",
                        "GRADLE_OPTIONS is --no-daemon --rerun-tasks -PBUILD_NUMBER=1 -PBRANCH=\"master\"",
                        "MULTILINE_SINGLE is \nLook at me 'here'",
                        "MULTILINE_DOUBLE is \nThe branch name is \"master\"")
                .go();
    }

    @Issue("JENKINS-42748")
    @Test
    public void envBackslashes() throws Exception {
        expect("envBackslashes")
                .logContains("[Pipeline] { (foo)",
                        "echo SIMPLE_BACKSLASH is C:\\hey",
                        "echo NESTED_BACKSLASH is C:\\hey\\there",
                        "echo HAS_TAB is oh\they",
                        "echo NESTED_HAS_TAB is oh\they\tthere",
                        "shell SIMPLE_BACKSLASH is C:\\hey",
                        "shell NESTED_BACKSLASH is C:\\hey\\there",
                        "shell HAS_TAB is oh\they",
                        "shell NESTED_HAS_TAB is oh\they\tthere")
                .go();
    }

    @Issue("JENKINS-41890")
    @Test
    public void environmentWithWorkspace() throws Exception {
        expect("environmentWithWorkspace")
                .logContains("[Pipeline] { (foo)",
                        "FOO is FOO",
                        "BAZ is FOOBAZ")
                .logMatches("BAR is .*?workspace" + Pattern.quote(File.separator) + "test\\d+BAR")
                .go();
    }

    @Test
    public void environmentWithVariable() throws Exception {
        expect("environmentWithVariable")
                .logContains("[Pipeline] { (foo)",
                             "FOO is BAR")
                .go();
    }


    @Test
    public void nonLiteralEnvironment() throws Exception {
        initGlobalLibrary();

        expect("nonLiteralEnvironment")
                .logContains("[Pipeline] { (foo)",
                        "FOO is BAR",
                        "BUILD_NUM_ENV is 1",
                        "ANOTHER_ENV is 1",
                        "INHERITED_ENV is 1 is inherited",
                        "ACME_FUNC is banana tada",
                        "JUST_A_CONSTANT is 3")
                .go();
    }

    @Issue("JENKINS-43486")
    @Test
    public void booleanParamAndEnv() throws Exception {
        expect("booleanParamAndEnv")
                .logContains("hello")
                .go();
    }

    @Issue("JENKINS-43486")
    @Test
    public void nullParamAndEnv() throws Exception {
        expect("nullParamAndEnv")
                .logContains("hello")
                .go();
    }
}
