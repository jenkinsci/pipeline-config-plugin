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

package org.jenkinsci.plugins.pipeline.modeldefinition.withscript;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import hudson.model.AbstractDescribableImpl;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.net.URL;
import org.apache.commons.lang.reflect.ConstructorUtils;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsThread;

/**
 * Implementations for {@link WithScriptDescriptor} - pluggable script backends for Declarative
 * Pipelines.
 *
 * @author Andrew Bayer
 */
public abstract class WithScriptDescribable<T extends WithScriptDescribable<T>>
    extends AbstractDescribableImpl<T> implements Serializable {

  /**
   * ONLY TO BE RUN FROM WITHIN A CPS THREAD. Parses the script source and loads it. TODO: Decide if
   * we want to cache the resulting objects or just *shrug* and re-parse them every time.
   *
   * @return The script object for this.
   * @throws Exception if the script source cannot be loaded or we're called from outside a
   *     CpsThread.
   */
  @SuppressWarnings("unchecked")
  public WithScriptScript getScript(CpsScript cpsScript) throws Exception {
    CpsThread c = CpsThread.current();
    if (c == null) throw new IllegalStateException("Expected to be called from CpsThread");

    Class clz;
    try {
      clz = cpsScript.getClass().getClassLoader().loadClass(getDescriptor().getScriptClass());
    } catch (ClassNotFoundException e) {
      // This is special casing to deal with PluginFirstClassLoaders, which don't have a functional
      // findResource method.
      // That results in GroovyClassLoader.loadClass failing to find resources to parse and load.
      // TODO delete JENKINS-44898 workaround as of Jenkins 2.66
      URL res =
          getDescriptor()
              .getClass()
              .getClassLoader()
              .getResource(getDescriptor().getScriptClass().replace('.', '/') + ".groovy");
      if (res != null) {
        clz =
            ((GroovyClassLoader) cpsScript.getClass().getClassLoader())
                .parseClass(new GroovyCodeSource(res));
      } else {
        throw e;
      }
    }
    final Constructor constructor =
        ConstructorUtils.getMatchingAccessibleConstructor(
            clz, new Class[] {CpsScript.class, this.getClass()});
    if (constructor == null) {
      // Restoring same behaviour as Class.getConstructor
      throw new NoSuchMethodException(
          clz.getName()
              + ".<init>("
              + CpsScript.class.getName()
              + ","
              + this.getClass().getName()
              + ")");
    }
    return (WithScriptScript) constructor.newInstance(cpsScript, this);
  }

  @Override
  public WithScriptDescriptor getDescriptor() {
    return (WithScriptDescriptor) super.getDescriptor();
  }
}
