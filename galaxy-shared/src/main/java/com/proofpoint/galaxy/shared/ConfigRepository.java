package com.proofpoint.galaxy.shared;

import java.net.URI;

public interface ConfigRepository
{
    URI getConfigFile(ConfigSpec configSpec);
}
