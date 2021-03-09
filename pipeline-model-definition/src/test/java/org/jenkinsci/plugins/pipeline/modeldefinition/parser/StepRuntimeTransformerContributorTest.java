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
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.codehaus.groovy.ast.expr.*;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStep;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

public class StepRuntimeTransformerContributorTest extends AbstractModelDefTest {

  @Test
  public void simpleTransform() throws Exception {
    expect("simplePipeline").logContains("HELLO").go();
  }

  @Test
  public void nestedTransform() throws Exception {
    expect("steps/nestedTreeSteps").logContains("HELLO", "Timeout set to expire in 10 sec").go();
  }

  @Test
  public void transformPost() throws Exception {
    expect("transformPost")
        .logContains("[Pipeline] { (foo)", "HELLO", "I HAVE FINISHED", "MOST DEFINITELY FINISHED")
        .logNotContains("I FAILED")
        .go();
  }

  @TestExtension
  public static class EchoTransformer extends StepRuntimeTransformerContributor {
    @Override
    @NonNull
    public MethodCallExpression transformStep(
        @NonNull ModelASTStep step, @NonNull MethodCallExpression methodCall) {
      if (step.getName().equals("echo")) {
        ArgumentListExpression newArgs = new ArgumentListExpression();
        TupleExpression oldArgs = (TupleExpression) methodCall.getArguments();

        for (Expression expr : oldArgs.getExpressions()) {
          if (expr instanceof ConstantExpression
              && ((ConstantExpression) expr).getValue() instanceof String) {
            String origVal = (String) ((ConstantExpression) expr).getValue();
            newArgs.addExpression(constX(origVal.toUpperCase()));
          }
        }
        methodCall.setArguments(newArgs);
      }

      return methodCall;
    }
  }

  @TestExtension
  public static class TimeoutTransformer extends StepRuntimeTransformerContributor {
    @Override
    @NonNull
    public MethodCallExpression transformStep(
        @NonNull ModelASTStep step, @NonNull MethodCallExpression methodCall) {
      if (step.getName().equals("timeout")) {
        ArgumentListExpression newArgs = new ArgumentListExpression();
        TupleExpression oldArgs = (TupleExpression) methodCall.getArguments();

        for (Expression expr : oldArgs.getExpressions()) {
          if (expr instanceof MapExpression) {
            MapExpression originalMap = (MapExpression) expr;
            MapExpression newMap = new MapExpression();

            for (MapEntryExpression origEntry : originalMap.getMapEntryExpressions()) {
              if (origEntry.getKeyExpression() instanceof ConstantExpression
                  && ((ConstantExpression) origEntry.getKeyExpression())
                      .getValue()
                      .equals("time")) {
                newMap.addMapEntryExpression(constX("time"), constX(10));
              } else {
                newMap.addMapEntryExpression(origEntry);
              }
            }
            newArgs.addExpression(newMap);
          } else {
            newArgs.addExpression(expr);
          }
        }
        methodCall.setArguments(newArgs);
      }

      return methodCall;
    }
  }
}
