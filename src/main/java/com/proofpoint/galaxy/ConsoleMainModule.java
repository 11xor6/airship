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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;

public class ConsoleMainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.bind(Console.class).in(Scopes.SINGLETON);
        binder.bind(ConsoleSlotResource.class).in(Scopes.SINGLETON);
        binder.bind(ConsoleAssignmentResource.class).in(Scopes.SINGLETON);
        binder.bind(ConsoleLifecycleResource.class).in(Scopes.SINGLETON);
        binder.bind(AnnounceResource.class).in(Scopes.SINGLETON);
        binder.bind(RemoteSlotFactory.class).to(HttpRemoteSlotFactory.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder).to(ConsoleConfig.class);
    }
}
