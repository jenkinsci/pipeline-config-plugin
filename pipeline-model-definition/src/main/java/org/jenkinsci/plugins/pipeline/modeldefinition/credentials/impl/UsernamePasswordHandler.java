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

package org.jenkinsci.plugins.pipeline.modeldefinition.credentials.impl;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import org.jenkinsci.plugins.credentialsbinding.impl.UsernamePasswordBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.UsernamePasswordMultiBinding;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.*;

@Extension
public class UsernamePasswordHandler extends CredentialsBindingHandler<StandardUsernamePasswordCredentials> {

    @NonNull
    @Override
    public Class<? extends StandardCredentials> type() {
        return StandardUsernamePasswordCredentials.class;
    }

    @NonNull
    @Override
    public List<Map<String, Object>> getWithCredentialsParameters(String credentialsId) {
        Map<String, Object> map = new HashMap<>();
        map.put("$class", UsernamePasswordBinding.class.getName());
        map.put("variable", new EnvVarResolver());
        map.put("credentialsId", credentialsId);
        Map<String, Object> map2 = new HashMap<>();
        map2.put("$class", UsernamePasswordMultiBinding.class.getName());
        map2.put("usernameVariable", new EnvVarResolver("%s_USR"));
        map2.put("passwordVariable", new EnvVarResolver("%s_PSW"));
        map2.put("credentialsId", credentialsId);
        return Arrays.asList(map, map2);
    }

}
