package com.proofpoint.galaxy.cli;

import com.google.common.base.Predicate;
import com.proofpoint.galaxy.coordinator.AgentFilterBuilder;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.HttpUriBuilder;
import org.iq80.cli.Option;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;

public class AgentFilter
{
    @Option(name = {"-h", "--host"}, description = "Select agent on the given host")
    public final List<String> host = newArrayList();

    @Option(name = {"-u", "--uuid"}, description = "Select agent containing a slot the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select agent containing 'r{unning}', 's{topped}' or 'unknown' slots")
    public final List<String> state = newArrayList();

    public Predicate<AgentStatus> toAgentPredicate(List<UUID> allUuids)
    {
        return createFilterBuilder().build(allUuids);
    }

    public URI toUri(URI baseUri)
    {
        return createFilterBuilder().buildUri(baseUri);
    }

    public URI toUri(HttpUriBuilder uriBuilder)
    {
        return createFilterBuilder().buildUri(uriBuilder);
    }

    private AgentFilterBuilder createFilterBuilder()
    {
        AgentFilterBuilder agentFilterBuilder = AgentFilterBuilder.builder();
        for (String hostGlob : host) {
            agentFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String stateFilter : state) {
            agentFilterBuilder.addStateFilter(stateFilter);
        }
        for (String uuidGlob : uuid) {
            agentFilterBuilder.addSlotUuidGlobFilter(uuidGlob);
        }
        return agentFilterBuilder;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Filter");
        sb.append("{host=").append(host);
        sb.append(", uuid=").append(uuid);
        sb.append(", state=").append(state);
        sb.append('}');
        return sb.toString();
    }

}
