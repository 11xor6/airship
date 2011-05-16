package com.proofpoint.galaxy.coordinator;

import com.google.common.io.InputSupplier;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;

@Path("/v1/git")
public class GitConfigResource
{
    private final GitConfigRepository gitConfigRepository;

    @Inject
    public GitConfigResource(GitConfigRepository gitConfigRepository)
    {
        this.gitConfigRepository = gitConfigRepository;
    }

    @GET
    @Path("blob/{objectId}")
    public Response getConfigFile(@PathParam("objectId") String objectId)
    {
        InputSupplier<InputStream> blob = gitConfigRepository.getBlob(objectId);
        if (blob == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(new InputSupplierStreamingOutput(blob)).build();
    }
}
