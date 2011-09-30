package com.proofpoint.galaxy.agent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.log.Logger;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.net.ConnectException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.json.JsonCodec.jsonCodec;

public class HttpAnnouncementService implements AnnouncementService
{
    private static final Logger log = Logger.get(HttpAnnouncementService.class);
    private static final JsonCodec<AgentStatusRepresentation> codec = jsonCodec(AgentStatusRepresentation.class);

    private final Agent agent;
    private final HttpServerInfo httpServerInfo;
    private final AsyncHttpClient client;
    private String announcementUrl;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> announcementTask;

    @Inject
    public HttpAnnouncementService(AgentConfig agentConfig, Agent agent, HttpServerInfo httpServerInfo)
    {
        this.agent = agent;
        this.httpServerInfo = httpServerInfo;
        this.announcementUrl = agentConfig.getCoordinatorBaseURI().resolve("/v1/announce/" + agent.getAgentId()).toString();
        this.client = new AsyncHttpClient();
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("announce-%s").build());
    }

    @PostConstruct
    public synchronized void start()
    {
        // already started?
        if (announcementTask != null) {
            return;
        }

        announcementTask = executor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    announce();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, 0, 3, TimeUnit.SECONDS);

    }

    public synchronized void stop()
    {
        // already stopped?
        if (announcementTask == null) {
            return;
        }

        // todo delete announcement
        announcementTask.cancel(true);
        announcementTask = null;
    }

    @Override
    public synchronized void announce()
            throws InterruptedException
    {
        try {
            AgentStatus agentStatus = agent.getAgentStatus();
            AgentStatusRepresentation agentStatusRepresentation = AgentStatusRepresentation.from(agentStatus);
            String json = codec.toJson(agentStatusRepresentation);

            Response response = client.preparePut(announcementUrl)
                    .setBody(json)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .execute()
                    .get();
            if (response.getStatusCode() / 100 != 2) {
                log.warn("Announcement to " + announcementUrl + " failed: " + response.getStatusText());
            }
        }
        catch (InterruptedException e) {
            throw e;
        }
        catch (Exception e) {
            if (e.getCause() instanceof ConnectException) {
                log.warn("Could not connect to coordinator at " + announcementUrl);
            } else {
                log.warn(e, "Error announcing status to " + announcementUrl);
            }
        }
    }
}
