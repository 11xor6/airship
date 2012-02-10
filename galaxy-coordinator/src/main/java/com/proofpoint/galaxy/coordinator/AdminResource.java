package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Predicate;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.CoordinatorStatus;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.List;

import static com.google.common.collect.Collections2.transform;
import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.fromAgentStatus;
import static com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation.fromCoordinatorStatus;

@Path("/v1/admin/")
public class AdminResource
{
    private final Coordinator coordinator;

    @Inject
    public AdminResource(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    @GET
    @Path("/coordinator")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllCoordinators(@Context UriInfo uriInfo)
    {
        Predicate<CoordinatorStatus> coordinatorPredicate = CoordinatorFilterBuilder.build(uriInfo);
        List<CoordinatorStatus> coordinators = coordinator.getCoordinators(coordinatorPredicate);
        return Response.ok(transform(coordinators, fromCoordinatorStatus())).build();
    }

    @GET
    @Path("/agent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAgents(@Context UriInfo uriInfo)
    {
        List<AgentStatus> agents = coordinator.getAllAgentStatus();
        return Response.ok(transform(agents, fromAgentStatus())).build();
    }

    @POST
    @Path("/agent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response provisionAgent(
            AgentProvisioningRepresentation provisioning,
            @DefaultValue("1") @QueryParam("count") int count,
            @Context UriInfo uriInfo)
            throws Exception
    {
        List<AgentStatus> agents = coordinator.provisionAgents(count, provisioning.getInstanceType(), provisioning.getAvailabilityZone());

        return Response.ok(transform(agents, fromAgentStatus())).build();
    }

    @DELETE
    @Path("/agent/{agentId: [a-z0-9-]+}")
    public Response terminateAgent(String agentId, @Context UriInfo uriInfo)
    {
        if (coordinator.terminateAgent(agentId) != null) {
            return Response.ok().build();
        }
        return Response.status(Status.NOT_FOUND).build();
    }
}
