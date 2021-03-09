/*
 * The MIT License
 *
 * Copyright (c) 2021, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.when;

import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.CommonUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.InvisibleGlobalWhenCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTWhenContent;

/** Base descriptor for {@link GlobalStageConditional} */
public abstract class GlobalStageConditionalDescriptor<S extends GlobalStageConditional<S>>
    extends DeclarativeStageConditionalDescriptor<S> {

  /**
   * Generates a map of strings to objects which {@link #transformToRuntimeAST(ModelASTWhenContent)}
   * will use for instantiating the {@link GlobalStageConditional} for this descriptor.
   *
   * @param when The when condition to be inspected
   * @return A map of arguments to use for instantiation.
   */
  public abstract Map<String, Object> argMapForCondition(
      @NonNull InvisibleGlobalWhenCondition when);

  @Override
  public final boolean isInvisible() {
    return true;
  }

  @Override
  public final Expression transformToRuntimeAST(@CheckForNull ModelASTWhenContent when) {
    if (when instanceof InvisibleGlobalWhenCondition) {
      InvisibleGlobalWhenCondition invisibleWhen = (InvisibleGlobalWhenCondition) when;

      MapExpression m = new MapExpression();
      argMapForCondition(invisibleWhen)
          .forEach((k, v) -> m.addMapEntryExpression(constX(k), constX(v)));

      return callX(
          ClassHelper.make(CommonUtils.class), "instantiateDescribable", args(classX(clazz), m));
    }
    return GeneralUtils.constX(null);
  }
}
