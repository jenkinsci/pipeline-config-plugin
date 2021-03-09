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

package org.jenkinsci.plugins.pipeline.modeldefinition.generator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

public class EnvironmentDirective extends AbstractDirective<EnvironmentDirective> {
  private final List<NameAndValue> env = new ArrayList<>();

  @DataBoundConstructor
  public EnvironmentDirective(List<NameAndValue> env) {
    if (env != null) {
      this.env.addAll(env);
    }
  }

  public List<NameAndValue> getEnv() {
    return env;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<EnvironmentDirective> {
    @Override
    @NonNull
    public String getName() {
      return "environment";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Environment";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      return Collections.emptyList();
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull EnvironmentDirective directive) {
      StringBuilder result = new StringBuilder("environment {\n");
      if (!directive.getEnv().isEmpty()) {
        for (NameAndValue e : directive.getEnv()) {
          result.append(e.name).append(" = ");
          result.append("\"").append(e.getValue()).append("\"\n");
        }
      } else {
        result.append("// No environment variables specified\n");
      }
      result.append("}\n");

      return result.toString();
    }

    public String getEnvHelp(String field) {
      return "/descriptor/" + getId() + "/help/" + field;
    }
  }

  @Restricted(NoExternalUse.class)
  public static final class NameAndValue {
    private String name;
    private String value;

    @DataBoundConstructor
    public NameAndValue(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }
  }
}
