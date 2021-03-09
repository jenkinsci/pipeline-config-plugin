/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package org.jenkinsci.plugins.pipeline.modeldefinition.generator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.kohsuke.stapler.DataBoundConstructor;

public class AxisDirective extends AbstractDirective<AxisDirective> {

  private String name;
  private String values;
  private boolean notValues;

  @DataBoundConstructor
  public AxisDirective(String name, String values, boolean notValues) {
    this.name = name;
    this.values = values;
    this.notValues = notValues;
  }

  @NonNull
  public String getName() {
    return name;
  }

  public String getValues() {
    return values;
  }

  public boolean isNotValues() {
    return notValues;
  }

  static String tokenize(String values) {
    return Arrays.asList(values.split("\\s*,\\s*")).stream()
        .map(token -> String.format("'%s'", token))
        .collect(Collectors.joining(","));
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<AxisDirective> {

    @NonNull
    @Override
    public boolean isTopLevel() {
      return false;
    }

    @Override
    @NonNull
    public String getName() {
      return "axis";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Axis";
    }

    @NonNull
    @Override
    public List<Descriptor> getDescriptors() {
      return Collections.emptyList();
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull AxisDirective axis) {
      StringBuffer sb = new StringBuffer();
      sb.append("axis {\n");
      sb.append("name '" + axis.getName() + "'\n");
      if (axis.notValues) sb.append("notValues " + tokenize(axis.getValues()) + "\n");
      else sb.append("values " + tokenize(axis.getValues()) + "\n");
      sb.append("}");
      return sb.toString();
    }
  }
}
