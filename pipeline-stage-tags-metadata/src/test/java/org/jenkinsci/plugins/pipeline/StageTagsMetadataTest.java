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
 */

package org.jenkinsci.plugins.pipeline;

import static org.junit.Assert.*;

import hudson.ExtensionList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class StageTagsMetadataTest {
  @Rule public JenkinsRule rule = new JenkinsRule();

  @Test
  public void stageStatus() throws Exception {
    ExtensionList<StageTagsMetadata> list = rule.jenkins.getExtensionList(StageTagsMetadata.class);
    StageStatus stageStatus = list.get(StageStatus.class);
    assertNotNull(stageStatus);

    assertFalse(stageStatus.takesArbitraryValues());

    assertEquals(StageStatus.TAG_NAME, stageStatus.getTagName());

    List<String> vals = stageStatus.getPossibleValues();

    assertTrue(vals.contains(StageStatus.getFailedAndContinued()));
    assertTrue(vals.contains(StageStatus.getSkippedForConditional()));
    assertTrue(vals.contains(StageStatus.getSkippedForFailure()));
    assertTrue(vals.contains(StageStatus.getSkippedForUnstable()));
  }

  @Test
  public void syntheticStage() throws Exception {
    ExtensionList<StageTagsMetadata> list = rule.jenkins.getExtensionList(StageTagsMetadata.class);
    SyntheticStage synthetic = list.get(SyntheticStage.class);
    assertNotNull(synthetic);

    assertFalse(synthetic.takesArbitraryValues());

    assertEquals(SyntheticStage.TAG_NAME, synthetic.getTagName());

    List<String> vals = synthetic.getPossibleValues();

    assertTrue(vals.contains(SyntheticStage.getPre()));
    assertTrue(vals.contains(SyntheticStage.getPost()));
  }
}
