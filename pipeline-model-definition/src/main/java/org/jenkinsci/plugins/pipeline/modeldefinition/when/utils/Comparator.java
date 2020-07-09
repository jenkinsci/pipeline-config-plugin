/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.when.utils;

import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.Messages;
import org.jvnet.localizer.Localizable;

import javax.annotation.Nonnull;
import java.io.File;

import static org.apache.commons.lang.StringUtils.defaultIfBlank;

/**
 * Utility for selecting a way to compare two strings.
 */
public enum Comparator {
    /**
     * ANT style "glob" pattern.
     */
    GLOB(Messages._Comparator_GLOB_DisplayName()) {
        @Override
        public boolean compare(@Nonnull String pattern, String actual) {
            return compare(pattern, actual, false);
        }
        @Override
        public boolean compare(@Nonnull String pattern, String actual, boolean caseSensitive) {
            actual = defaultIfBlank(actual, "");
            // replace with the platform specific directory separator before
            // invoking Ant's platform specific path matching.
            String safeCompare = pattern.replace('/', File.separatorChar);
            String safeName = actual.replace('/', File.separatorChar);
            return SelectorUtils.matchPath(safeCompare, safeName, caseSensitive);
        }
    },
    /**
     * Regular expression
     */
    REGEXP(Messages._Comparator_REGEXP_DisplayName()) {
        @Override
        public boolean compare(@Nonnull String pattern, String actual) {
            actual = defaultIfBlank(actual, "");
            //TODO validation for pattern compile
            return actual.matches(pattern);
        }
        @Override
        public boolean compare(@Nonnull String pattern, String actual, boolean caseSensitive) {
            return compare(pattern, actual);
        }
    },
    /**
     * String equals
     */
    EQUALS(Messages._Comparator_EQUALS_DisplayName()) {
        @Override
        public boolean compare(@Nonnull String pattern, String actual) {
            return compare(pattern, actual, true);
        }
        @Override
        public boolean compare(@Nonnull String pattern, String actual, boolean caseSensitive) {
            actual = defaultIfBlank(actual, "");
            if (caseSensitive) {
                return actual.equals(pattern);
            } else {
                return actual.equalsIgnoreCase(pattern);
            }
        }
    };

    private final Localizable displayName;

    private Comparator(Localizable displayName) {
        this.displayName = displayName;
    }

    public Localizable getDisplayName() {
        return displayName;
    }

    /**
     * Compare the two strings
     * @param pattern the pattern/value to check for
     * @param actual the value to check
     * @param caseSensitive whether the comparison will be case-sensitive. Only for the GLOB comparator
     * @return true if matching
     */
    public abstract boolean compare(String pattern, String actual, boolean caseSensitive);

    /**
     * Compare the two strings
     * @param pattern the pattern/value to check for
     * @param actual the value to check
     * @return true if matching
     */
    public abstract boolean compare(String pattern, String actual);

    public static Comparator get(String name, Comparator defaultValue) {
        if (StringUtils.isEmpty(name)) {
            return defaultValue;
        }
        for (Comparator comparator : Comparator.values()) {
            if (name.equalsIgnoreCase(comparator.name())) {
                return comparator;
            }
        }
        return defaultValue;
    }

    public static ListBoxModel getSelectOptions(boolean emptySelection, Comparator top) {
        ListBoxModel model = new ListBoxModel();
        if (emptySelection) {
            model.add("");
        }
        if (top != null) {
            model.add(top.getDisplayName().toString(), top.name());
        }
        for (Comparator comparator : Comparator.values()) {
            if (comparator != top) {
                model.add(comparator.getDisplayName().toString(), comparator.name());
            }
        }
        return model;
    }
}
