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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/** A {@link org.hamcrest.Matcher} on the content of an {@link InputStream} as a {@link String}. */
public class InputStreamContainingString extends TypeSafeMatcher<InputStream> {

  @NonNull private final Matcher<String> contentMatcher;
  @CheckForNull private final Charset encoding;

  public InputStreamContainingString(
      @NonNull Matcher<String> contentMatcher, @CheckForNull Charset encoding) {
    this.contentMatcher = contentMatcher;
    this.encoding = encoding;
  }

  @Override
  protected boolean matchesSafely(InputStream item) {
    try {
      String s = IOUtils.toString(item, encoding);
      return contentMatcher.matches(s);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read to String.", e);
    }
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("InputStream containing ").appendDescriptionOf(contentMatcher);
  }

  public static Matcher<InputStream> inputStream(
      Matcher<String> matcher, @CheckForNull Charset encoding) {
    return new InputStreamContainingString(matcher, encoding);
  }

  public static Matcher<InputStream> inputStream(Matcher<String> matcher) {
    return inputStream(matcher, null);
  }
}
