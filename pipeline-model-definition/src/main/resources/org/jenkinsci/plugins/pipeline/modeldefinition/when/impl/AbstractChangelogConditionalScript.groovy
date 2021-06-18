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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.impl

import hudson.model.Item
import hudson.scm.ChangeLogSet
import jenkins.scm.api.SCMHead
import org.jenkinsci.plugins.pipeline.modeldefinition.when.ChangeLogStrategy
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditional
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalScript
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

abstract class AbstractChangelogConditionalScript<S extends DeclarativeStageConditional<S>> extends DeclarativeStageConditionalScript<S> {

    AbstractChangelogConditionalScript(CpsScript s, S c) {
        super(s, c)
    }

    @Override
    boolean evaluate() {
        initializeEval()
        RunWrapper run = (RunWrapper)this.script.getProperty("currentBuild")
        if (run != null) {
            List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = []
            def head = SCMHead.HeadByItem.findHead((Item)run.rawBuild.parent)
            if (head != null) {
                /*
                  Some special handling for pull requests to take into consideration all the builds for a particular PR.
                  Since a PR is a series of changes that will be merged in some way as one unit so all the changes should be considered.
                  There is a difference in for example Gerrit where the change that is going to be merged is only the one commit in the latest patch set,
                  so the previous builds in the change request are not really dependant on each other.
                  Otherwise we could have just done this for all ChangeRequestSCMHead instances.
                  A better approach than checking each specific implementation would be nice.
                  There are some caveats here, like if build 3 contains a revert commit of what is in build 2
                  we will still "trigger" for change sets on the commit that was reverted.
                */
                //TODO JENKINS-33274

                if (ChangeLogStrategy.isExamineAllBuilds(head)) {
                    script.echo "Examining changelog from all builds of this change request."
                    for (RunWrapper currB = run; currB != null; currB = currB.previousBuild) {
                        changeSets.addAll(currB.getChangeSets())
                    }
                }
            }

            if (changeSets.isEmpty()) { //in case none of the above applies
                changeSets = run.getChangeSets()
            }


            if (changeSets.isEmpty()) {
                if (run.number <= 1) {
                    script.echo "Warning, empty changelog. Probably because this is the first build." //TODO JENKINS-46086
                } else {
                    script.echo "Warning, empty changelog. Have you run checkout?"
                }
                return false
            }
            return changeSetsMatches(changeSets);
        }
        return false
    }

    abstract boolean matches(ChangeLogSet.Entry change)

    boolean changeSetsMatches(List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets) {
        return changeSets.any {def set ->
            return set.any { def change ->
                return matches(change)
            }
        }
    }

    void initializeEval() {}
}
