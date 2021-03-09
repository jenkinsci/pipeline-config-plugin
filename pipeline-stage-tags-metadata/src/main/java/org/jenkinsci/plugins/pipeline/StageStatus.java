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

package org.jenkinsci.plugins.pipeline;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jenkinsci.plugins.workflow.actions.TagsAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

@Extension
public class StageStatus extends StageTagsMetadata {

  public static final String TAG_NAME = "STAGE_STATUS";

  @Override
  public String getTagName() {
    return TAG_NAME;
  }

  @Override
  public List<String> getPossibleValues() {
    List<String> vals = new ArrayList<>();

    // When there are other categories of status values, we'll add their methods too.
    vals.addAll(getSkippedStageValues());
    vals.add(getFailedAndContinued());

    return vals;
  }

  public List<String> getSkippedStageValues() {
    return skippedStages();
  }

  @NonNull
  public static List<String> skippedStages() {
    return Arrays.asList(
        getSkippedForConditional(),
        getSkippedForFailure(),
        getSkippedForUnstable(),
        getSkippedForRestart());
  }

  public static String getFailedAndContinued() {
    return "FAILED_AND_CONTINUED";
  }

  public static String getSkippedForFailure() {
    return "SKIPPED_FOR_FAILURE";
  }

  public static String getSkippedForUnstable() {
    return "SKIPPED_FOR_UNSTABLE";
  }

  public static String getSkippedForConditional() {
    return "SKIPPED_FOR_CONDITIONAL";
  }

  public static String getSkippedForRestart() {
    return "SKIPPED_FOR_RESTART";
  }

  public static boolean isSkippedStage(@NonNull FlowNode node) {
    TagsAction tagsAction = node.getPersistentAction(TagsAction.class);
    if (tagsAction != null) {
      String tagValue = tagsAction.getTagValue(TAG_NAME);
      return tagValue != null && skippedStages().contains(tagValue);
    }

    return false;
  }

  public static boolean isSkippedStageForReason(@NonNull FlowNode node, @NonNull String reason) {
    if (skippedStages().contains(reason)) {
      TagsAction tagsAction = node.getPersistentAction(TagsAction.class);
      if (tagsAction != null) {
        String tagValue = tagsAction.getTagValue(TAG_NAME);
        return tagValue != null && tagValue.equals(reason);
      }
    }
    return false;
  }
}
