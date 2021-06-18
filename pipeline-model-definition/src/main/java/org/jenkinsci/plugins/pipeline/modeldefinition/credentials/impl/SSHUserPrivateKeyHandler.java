package org.jenkinsci.plugins.pipeline.modeldefinition.credentials.impl;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.Extension;
import org.jenkinsci.plugins.credentialsbinding.impl.SSHUserPrivateKeyBinding;
import org.jenkinsci.plugins.pipeline.modeldefinition.model.CredentialsBindingHandler;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Extension
public class SSHUserPrivateKeyHandler extends CredentialsBindingHandler<SSHUserPrivateKey> {

    @NonNull
    @Override
    public Class<? extends StandardCredentials> type() {
        return SSHUserPrivateKey.class;
    }

    @NonNull
    @Override
    public List<Map<String, Object>> getWithCredentialsParameters(String credentialsId) {
        Map<String, Object> map = new HashMap<>();
        map.put("$class", SSHUserPrivateKeyBinding.class.getName());
        map.put("keyFileVariable", new EnvVarResolver());
        map.put("usernameVariable", new EnvVarResolver("%s_USR"));
        map.put("passphraseVariable", new EnvVarResolver("%s_PSW"));
        map.put("credentialsId", credentialsId);
        return Collections.singletonList(map);
    }
}
