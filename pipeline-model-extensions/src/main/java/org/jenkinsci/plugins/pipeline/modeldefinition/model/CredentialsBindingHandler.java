/*
 * The MIT License
 *
 * Copyright (c) 2016-2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.modeldefinition.model;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;
import java.io.Serializable;
import java.util.*;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException;

/**
 * Simplified {@link org.jenkinsci.plugins.credentialsbinding.Binding} handler for use in {@code
 * environment {} }
 */
public abstract class CredentialsBindingHandler<C extends StandardCredentials>
    implements ExtensionPoint {

  public boolean handles(Class<? extends StandardCredentials> c) {
    return type().isAssignableFrom(c);
  }

  public boolean handles(StandardCredentials c) {
    return handles(c.getClass());
  }

  @NonNull
  public List<MultiBinding<C>> toBindings(String varName, String credentialsId) {
    return Collections.emptyList();
  }

  @NonNull
  public abstract Class<? extends StandardCredentials> type();

  @NonNull
  public abstract List<Map<String, Object>> getWithCredentialsParameters(String credentialsId);

  @NonNull
  public static ExtensionList<CredentialsBindingHandler> all() {
    return ExtensionList.lookup(CredentialsBindingHandler.class);
  }

  @NonNull
  public static Set<Class<? extends StandardCredentials>> supportedTypes() {
    Set<Class<? extends StandardCredentials>> set = new HashSet<>();
    for (CredentialsBindingHandler<?> handler : all()) {
      set.add(handler.type());
    }
    return set;
  }

  @NonNull
  public static Set<String> supportedTypeNames() {
    Set<String> set = new HashSet<>();
    for (Class<? extends StandardCredentials> c : supportedTypes()) {
      set.add(c.getSimpleName());
    }
    return set;
  }

  @CheckForNull
  public static CredentialsBindingHandler forCredential(StandardCredentials c) {
    for (CredentialsBindingHandler handler : all()) {
      if (handler.handles(c)) {
        return handler;
      }
    }
    return null;
  }

  @NonNull
  public static CredentialsBindingHandler forId(String id, Run context)
      throws CredentialNotFoundException {
    IdCredentials cred = CredentialsProvider.findCredentialById(id, IdCredentials.class, context);
    if (cred == null) {
      throw new CredentialNotFoundException(id);
    }
    if (cred instanceof StandardCredentials) {
      CredentialsBindingHandler handler = forCredential((StandardCredentials) cred);
      if (handler == null) {
        throw new CredentialNotFoundException(
            String.format(
                "No suitable binding handler could be found for type %s. "
                    + "Supported types are %s.",
                cred.getClass().getName(), StringUtils.join(supportedTypeNames(), ',')));
      }
      return handler;
    } else {
      throw new CredentialNotFoundException(
          String.format(
              "Credentials %s is of type %s where " + "StandardCredentials is the expected type.",
              id, cred.getClass().getName()));
    }
  }

  public static class EnvVarResolver implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String value;

    public EnvVarResolver() {
      this.value = "%s";
    }

    public EnvVarResolver(String value) {
      this.value = value;
    }

    public String resolve(String varName) {
      return String.format(this.value, varName);
    }
  }
}
