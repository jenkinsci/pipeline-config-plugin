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
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class LibrariesDirective extends AbstractDirective<LibrariesDirective> {
  private final List<NameAndVersion> libs = new ArrayList<>();

  @DataBoundConstructor
  public LibrariesDirective(List<NameAndVersion> libs) {
    if (libs != null) {
      this.libs.addAll(libs);
    }
  }

  public List<NameAndVersion> getLibs() {
    return libs;
  }

  @Extension
  public static class DescriptorImpl extends DirectiveDescriptor<LibrariesDirective> {
    @Override
    @NonNull
    public String getName() {
      return "libraries";
    }

    @Override
    @NonNull
    public String getDisplayName() {
      return "Shared Libraries";
    }

    @Override
    @NonNull
    public List<Descriptor> getDescriptors() {
      return Collections.emptyList();
    }

    @Override
    @NonNull
    public String toGroovy(@NonNull LibrariesDirective directive) {
      StringBuilder result = new StringBuilder("libraries {\n");
      if (!directive.getLibs().isEmpty()) {
        for (NameAndVersion l : directive.getLibs()) {
          result.append("lib(").append(Snippetizer.object2Groovy(l.getLibString())).append(")\n");
        }
      } else {
        result.append("// No libraries specified\n");
      }
      result.append("}\n");

      return result.toString();
    }

    public String getLibHelp(String field) {
      return "/descriptor/" + getId() + "/help/" + field;
    }
  }

  @Restricted(NoExternalUse.class)
  public static final class NameAndVersion {
    private String name;
    private String version;

    @DataBoundConstructor
    public NameAndVersion(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    @DataBoundSetter
    public void setVersion(String version) {
      this.version = version;
    }

    public String getVersion() {
      return version;
    }

    public String getLibString() {
      if (StringUtils.isEmpty(version)) {
        return name;
      } else {
        return name + "@" + version;
      }
    }
  }
}
