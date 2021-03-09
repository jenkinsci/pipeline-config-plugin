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

package org.jenkinsci.plugins.pipeline.modeldefinition.parser;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import java.util.Map;
import java.util.TreeMap;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.AbstractModelDefTest;
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.InvisibleGlobalWhenCondition;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTBranch;
import org.jenkinsci.plugins.pipeline.modeldefinition.ast.ModelASTStage;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.GlobalStageConditional;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.GlobalStageConditionalDescriptor;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class RuntimeASTTransformerTest extends AbstractModelDefTest {
  private void setupGlobalConditionals(String skipStageName, int maxStepCount) {
    GlobalStageNameTestConditional.GlobalStageNameTestConditionalDescriptor nameDesc =
        ExtensionList.lookupSingleton(
            GlobalStageNameTestConditional.GlobalStageNameTestConditionalDescriptor.class);
    nameDesc.skipStageName = skipStageName;

    GlobalStepCountTestConditional.GlobalStepCountTestConditionalDescriptor stepDesc =
        ExtensionList.lookupSingleton(
            GlobalStepCountTestConditional.GlobalStepCountTestConditionalDescriptor.class);
    stepDesc.maxStepCount = maxStepCount;
  }

  @Test
  public void globalConditionalNoWhensMatching() throws Exception {
    setupGlobalConditionals("hello", 0);

    expect("twoStages").logNotContains("hello world").logContains("goodbye world").go();
  }

  @Test
  public void globalConditionalNoWhensNotMatching() throws Exception {
    setupGlobalConditionals("something else", 0);

    expect("twoStages").logContains("hello world", "goodbye world").go();
  }

  @Test
  public void globalConditionalStageInspectionMatching() throws Exception {
    setupGlobalConditionals(null, 1);

    expect("twoStages").logContains("hello world").logNotContains("goodbye world").go();
  }

  @Test
  public void globalConditionalStageInspectionNotMatching() throws Exception {
    setupGlobalConditionals(null, 0);
    expect("twoStages").logContains("hello world", "goodbye world").go();
  }

  @Test
  public void globalConditionalExistingWhensMatching() throws Exception {
    setupGlobalConditionals("Two", 0);

    expect("when/whenEnv")
        .logNotContains("Heal it", "Should never be reached")
        .logContains("Ignore case worked")
        .go();
  }

  @Test
  public void globalConditionalExistingWhensNotMatching() throws Exception {
    setupGlobalConditionals("something else", 0);

    expect("when/whenEnv")
        .logNotContains("Should never be reached")
        .logContains("Heal it", "Ignore case worked")
        .go();
  }

  public static class GlobalStageNameTestConditional
      extends GlobalStageConditional<GlobalStageNameTestConditional> {
    private final String skipStageName;
    private String stageName;

    @DataBoundConstructor
    public GlobalStageNameTestConditional(String skipStageName) {
      this.skipStageName = skipStageName;
    }

    @DataBoundSetter
    public void setStageName(String stageName) {
      this.stageName = stageName;
    }

    public String getStageName() {
      return stageName;
    }

    public String getSkipStageName() {
      return skipStageName;
    }

    @TestExtension
    @Symbol("globalStageNameTest")
    public static class GlobalStageNameTestConditionalDescriptor
        extends GlobalStageConditionalDescriptor<GlobalStageNameTestConditional> {
      public String skipStageName;

      @Override
      public Map<String, Object> argMapForCondition(@NonNull InvisibleGlobalWhenCondition when) {
        Map<String, Object> argMap = new TreeMap<>();

        if (when.getName().equals("globalStageNameTest")) {
          argMap.put("skipStageName", skipStageName);
          argMap.put("stageName", when.getStageName());
        }

        return argMap;
      }

      @NonNull
      @Override
      public String getScriptClass() {
        return getClass().getPackage().getName() + ".GlobalStageNameTestConditionalScript";
      }
    }
  }

  public static class GlobalStepCountTestConditional
      extends GlobalStageConditional<GlobalStepCountTestConditional> {
    private final int maxStepCount;
    private String stageJSON;

    @DataBoundConstructor
    public GlobalStepCountTestConditional(int maxStepCount) {
      this.maxStepCount = maxStepCount;
    }

    public int getMaxStepCount() {
      return maxStepCount;
    }

    @DataBoundSetter
    public void setStageJSON(String stageJSON) {
      this.stageJSON = stageJSON;
    }

    public boolean evaluate() throws Exception {
      if (stageJSON != null && maxStepCount > 0) {
        ModelASTStage stage = Utils.parseStageFromJSON(stageJSON);
        if (stage != null && stage.getBranches().size() > 0) {
          for (ModelASTBranch branch : stage.getBranches()) {
            if (branch.getSteps().size() > maxStepCount) {
              return false;
            }
          }
        }
      }
      return true;
    }

    @TestExtension
    @Symbol("globalStepCountTest")
    public static class GlobalStepCountTestConditionalDescriptor
        extends GlobalStageConditionalDescriptor<GlobalStepCountTestConditional> {
      public int maxStepCount;

      @Override
      public Map<String, Object> argMapForCondition(@NonNull InvisibleGlobalWhenCondition when) {
        Map<String, Object> argMap = new TreeMap<>();

        if (when.getName().equals("globalStepCountTest")
            && when.getStage() instanceof ModelASTStage) {
          argMap.put("maxStepCount", maxStepCount);
          argMap.put("stageJSON", when.getStage().toJSON().toString());
        }

        return argMap;
      }

      @NonNull
      @Override
      public String getScriptClass() {
        return getClass().getPackage().getName() + ".GlobalStepCountTestConditionalScript";
      }
    }
  }
}
