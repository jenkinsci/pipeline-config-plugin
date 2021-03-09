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

import hudson.EnvVars;
import org.apache.commons.lang.StringUtils;

/**
 * Easier way to refer to and extract known environment variables instead of copying strings back
 * and forth.
 */
public enum EnvironmentNames {
  CHANGE_ID,
  CHANGE_TARGET,
  CHANGE_BRANCH,
  CHANGE_FORK,
  CHANGE_URL,
  CHANGE_TITLE,
  CHANGE_AUTHOR,
  CHANGE_AUTHOR_DISPLAY_NAME,
  CHANGE_AUTHOR_EMAIL;

  public boolean exists(EnvVars vars) {
    return get(vars) != null;
  }

  public boolean isEmpty(EnvVars vars) {
    return StringUtils.isEmpty(get(vars));
  }

  public String get(EnvVars vars) {
    return vars.get(this.name());
  }
}
