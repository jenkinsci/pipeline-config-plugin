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


package org.jenkinsci.plugins.pipeline.modeldefinition

import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodMissingWrapper
import org.jenkinsci.plugins.pipeline.modeldefinition.model.MethodsToList
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable
import org.jenkinsci.plugins.workflow.cps.CpsScript

import static org.jenkinsci.plugins.pipeline.modeldefinition.Utils.isOfType

/**
 * Translates a closure containing a sequence of method calls into a {@link MethodsToList} implementation.
 *
 * @author Andrew Bayer
 */
public class MethodsToListTranslator implements MethodMissingWrapper, Serializable {
    List<Object> actualList = []
    CpsScript script
    Class<MethodsToList> clazz

    MethodsToListTranslator(CpsScript script, Class<MethodsToList> c) {
        this.script = script
        this.clazz = c
    }

    def methodMissing(String s, args) {
        def argVal
        if (args instanceof List || args instanceof Object[]) {
            if (args.size() > 0) {
                argVal = args[0]
            } else {
                argVal = null
            }
        } else {
            argVal = args
        }

        def retVal

        if (argVal != null) {
            retVal = script."${s}"(argVal)
        } else {
            retVal = script."${s}"()
        }

        if (retVal instanceof UninstantiatedDescribable &&
            isOfType((UninstantiatedDescribable)retVal, Utils.getMethodsToListType(clazz))) {
            actualList << retVal
        }


        return retVal
    }

    MethodsToList toListModel(Class<MethodsToList> c) {
        MethodsToList m = c.newInstance(actualList)

        return m
    }

}
