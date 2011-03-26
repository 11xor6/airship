/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

@Immutable
public class ConsoleAssignment
{
    private final BinarySpec binary;
    private final ConfigSpec config;

    public ConsoleAssignment(String binary, String config)
    {
        Preconditions.checkNotNull(binary, "binary is null");
        Preconditions.checkNotNull(config, "config is null");

        this.binary = BinarySpec.valueOf(binary);
        this.config = ConfigSpec.valueOf(config);
    }

    public ConsoleAssignment(BinarySpec binary, ConfigSpec config)
    {
        Preconditions.checkNotNull(binary, "binary is null");
        Preconditions.checkNotNull(config, "config is null");

        this.binary = binary;
        this.config = config;
    }

    public BinarySpec getBinary()
    {
        return binary;
    }

    public ConfigSpec getConfig()
    {
        return config;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ConsoleAssignment that = (ConsoleAssignment) o;

        if (!binary.equals(that.binary)) {
            return false;
        }
        if (!config.equals(that.config)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = binary.hashCode();
        result = 31 * result + config.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("ConsoleAssignment");
        sb.append("{binary=").append(binary);
        sb.append(", config=").append(config);
        sb.append('}');
        return sb.toString();
    }
}
