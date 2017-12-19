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

package org.jenkinsci.plugins.pipeline.modeldefinition.options;

import hudson.ExtensionList;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;

import javax.annotation.Nonnull;
import java.util.Set;

public abstract class DeclarativeOptionDescriptor extends Descriptor<DeclarativeOption> {

    public @Nonnull String getName() {
        Set<String> symbolValues = SymbolLookup.getSymbolValue(this);
        if (symbolValues.isEmpty()) {
            throw new IllegalArgumentException("Declarative Option descriptor class " + this.getClass().getName()
                    + " does not have a @Symbol and does not override getName().");
        }
        return symbolValues.iterator().next();
    }

    /**
     * If true, this option can be used in stages and is relevant there.
     */
    public boolean canUseInStage() {
        return false;
    }

    /**
     * Get all {@link DeclarativeOptionDescriptor}s.
     *
     * @return a list of all {@link DeclarativeOptionDescriptor}s registered.`
     */
    public static ExtensionList<DeclarativeOptionDescriptor> all() {
        return ExtensionList.lookup(DeclarativeOptionDescriptor.class);
    }
}
