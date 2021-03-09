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
 *
 */

package org.jenkinsci.plugins.pipeline.modeldefinition.util;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import jenkins.model.ArtifactManager;
import jenkins.util.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/** {@link Matcher} for {@link Run}s containing an archived file */
public class HasArchived extends TypeSafeMatcher<Run> {

  private final Matcher<String> nameMatcher;
  private final Matcher<?> contentMatcher;

  public HasArchived(@NonNull Matcher<String> nameMatcher, @NonNull Matcher<?> contentMatcher) {
    this.nameMatcher = nameMatcher;
    this.contentMatcher = contentMatcher;
  }

  @Override
  protected boolean matchesSafely(Run item) {
    ArtifactManager manager = item.getArtifactManager();
    try {
      VirtualFile root = manager.root();
      if (!root.exists()) {
        return false;
      }
      for (VirtualFile file : root.list()) {
        if (matchesFile(file, root)) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      throw new RuntimeException("Failed to examine archived files!", e);
    }
  }

  private boolean matchesFile(VirtualFile file, VirtualFile root) throws IOException {
    if (file.isDirectory()) {
      for (VirtualFile child : file.list()) {
        if (matchesFile(child, root)) {
          return true;
        }
      }
    } else if (nameMatcher.matches(fullName(file, root))) {
      try (InputStream stream = file.open()) {
        if (contentMatcher.matches(stream)) {
          return true;
        }
      }
    }
    return false;
  }

  private String fullName(VirtualFile file, VirtualFile root) throws IOException {
    StringBuilder name = new StringBuilder(file.getName());
    VirtualFile parent = file.getParent();
    while (parent != null && parent.isDirectory() && !parent.equals(root)) {
      if (!StringUtils.isEmpty(parent.getName())) {
        name.insert(0, parent.getName() + "/");
      }
      parent = parent.getParent();
    }
    return name.toString();
  }

  @Override
  public void describeTo(Description description) {
    description
        .appendText("Run that has archived file name:[")
        .appendDescriptionOf(nameMatcher)
        .appendText("] Content: [")
        .appendDescriptionOf(contentMatcher)
        .appendText("]");
  }

  public static Matcher<Run> hasArchived(Matcher<String> name, Matcher<?> content) {
    return new HasArchived(name, content);
  }

  public static Matcher<Run> hasArchivedString(
      Matcher<String> name, Matcher<String> content, Charset encoding) {
    return hasArchived(name, InputStreamContainingString.inputStream(content, encoding));
  }

  public static Matcher<Run> hasArchivedString(Matcher<String> name, Matcher<String> content) {
    return hasArchived(name, InputStreamContainingString.inputStream(content));
  }
}
