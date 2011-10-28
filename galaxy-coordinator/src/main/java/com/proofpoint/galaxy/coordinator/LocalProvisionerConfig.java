package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;
import java.util.List;

public class LocalProvisionerConfig
{
    private List<String> localAgentUri = ImmutableList.of();
    private String expectedStateDir = "expected-state";
    private String authorizedKeysDir = "authorized-keys";

    public List<String> getLocalAgentUris()
    {
        return localAgentUri;
    }

    @Config("coordinator.agent-uri")
    public LocalProvisionerConfig setLocalAgentUris(String localAgentUris)
    {
        this.localAgentUri = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(localAgentUris));
        return this;
    }

    @NotNull
    public String getExpectedStateDir()
    {
        return expectedStateDir;
    }

    @Config("coordinator.expected-state.dir")
    public LocalProvisionerConfig setExpectedStateDir(String expectedStateDir)
    {
        this.expectedStateDir = expectedStateDir;
        return this;
    }

    @NotNull
    public String getAuthorizedKeysDir()
    {
        return authorizedKeysDir;
    }

    @Config("coordinator.auth.authorized-keys-dir")
    @ConfigDescription("Directory of files named as the userid and containing authorized keys")
    public LocalProvisionerConfig setAuthorizedKeysDir(String authorizedKeysDir)
    {
        this.authorizedKeysDir = authorizedKeysDir;
        return this;
    }
}
