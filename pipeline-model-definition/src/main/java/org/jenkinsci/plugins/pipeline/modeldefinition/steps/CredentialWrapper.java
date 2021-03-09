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

package org.jenkinsci.plugins.pipeline.modeldefinition.steps;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/** Helper for simplified Credentials handling in {@code environment{}} */
public class CredentialWrapper implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String credentialId;
  private final List<Map<String, Object>> withCredentialsParameters;

  @Restricted(NoExternalUse.class)
  public CredentialWrapper(
      String credentialId, List<Map<String, Object>> withCredentialsParameters) {
    this.withCredentialsParameters = withCredentialsParameters;
    this.credentialId = credentialId;
  }

  @Whitelisted
  public String getCredentialId() {
    return credentialId;
  }

  @Whitelisted
  public void addParameters(String envVarName, List<Map<String, Object>> list) {
    list.addAll(resolveParameters(envVarName));
  }

  @Whitelisted
  public List<Map<String, Object>> resolveParameters(String envVarName) {
    List<Map<String, Object>> newList = new ArrayList<>(withCredentialsParameters.size());
    for (Map<String, Object> params : withCredentialsParameters) {
      Map<String, Object> newP = new HashMap<>();
      for (Map.Entry<String, Object> p : params.entrySet()) {
        Object value = p.getValue();
        if (value instanceof CredentialsBindingHandler.EnvVarResolver) {
          newP.put(
              p.getKey(), ((CredentialsBindingHandler.EnvVarResolver) value).resolve(envVarName));
        } else {
          newP.put(p.getKey(), p.getValue());
        }
      }
      newList.add(newP);
    }
    return newList;
  }
}
