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

import hudson.scm.ChangeLogSet
import org.jenkinsci.plugins.workflow.cps.CpsScript

class ChangeSetConditionalScript extends AbstractChangelogConditionalScript<ChangeSetConditional> {
    String glob

    ChangeSetConditionalScript(CpsScript s, ChangeSetConditional c) {
        super(s, c)
    }

    @Override
    boolean changeSetsMatches(List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets) {
        if (describable.shouldMatchAll) {
            return changeSets.every { def set ->
                return set.every { def change ->
                    return matches(change)
                }
            }
        } else {
            super.changeSetsMatches(changeSets);
        }
    }

    @Override
    boolean matches(ChangeLogSet.Entry change) {
        return describable.changeSetMatches(change, describable.pattern, describable.caseSensitive, describable.shouldMatchAll)
    }
}
