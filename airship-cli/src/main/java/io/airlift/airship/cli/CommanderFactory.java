package com.proofpoint.galaxy.cli;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.DeploymentManagerFactory;
import com.proofpoint.galaxy.agent.DirectoryDeploymentManagerFactory;
import com.proofpoint.galaxy.agent.LauncherLifecycleManager;
import com.proofpoint.galaxy.agent.LifecycleManager;
import com.proofpoint.galaxy.agent.Slot;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.CoordinatorConfig;
import com.proofpoint.galaxy.coordinator.HttpRepository;
import com.proofpoint.galaxy.coordinator.HttpServiceInventory;
import com.proofpoint.galaxy.coordinator.InMemoryStateManager;
import com.proofpoint.galaxy.coordinator.Instance;
import com.proofpoint.galaxy.coordinator.MavenRepository;
import com.proofpoint.galaxy.coordinator.Provisioner;
import com.proofpoint.galaxy.coordinator.RemoteAgent;
import com.proofpoint.galaxy.coordinator.RemoteAgentFactory;
import com.proofpoint.galaxy.coordinator.RemoteSlot;
import com.proofpoint.galaxy.coordinator.ServiceInventory;
import com.proofpoint.galaxy.coordinator.StateManager;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.CoordinatorLifecycleState;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.ExpectedSlotStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.RepositorySet;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.units.Duration;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Lists.newArrayList;

public class CommanderFactory
{
    private static final URI FAKE_LOCAL_URI = URI.create("java://localhost");
    private static final Duration COMMAND_TIMEOUT = new Duration(5, TimeUnit.MINUTES);

    private String environment;
    private URI coordinatorUri;
    private final List<String> repositories = newArrayList();
    private final List<String> mavenDefaultGroupIds = newArrayList();
    private String coordinatorId = "local";
    private String agentId = "local";
    private String location;
    private String instanceId = "local";
    private String instanceType = "local";
    private InetAddress internalIp;
    private String externalAddress;
    private boolean useInternalAddress;

    public CommanderFactory setEnvironment(String environment)
    {
        this.environment = environment;
        return this;
    }

    public CommanderFactory setCoordinatorUri(URI coordinatorUri)
    {
        this.coordinatorUri = coordinatorUri;
        return this;
    }

    public CommanderFactory setRepositories(List<String> repositories)
    {
        this.repositories.clear();
        this.repositories.addAll(repositories);
        return this;
    }

    public CommanderFactory setMavenDefaultGroupIds(List<String> mavenDefaultGroupIds)
    {
        this.mavenDefaultGroupIds.clear();
        this.mavenDefaultGroupIds.addAll(mavenDefaultGroupIds);
        return this;
    }

    public void setCoordinatorId(String coordinatorId)
    {
        Preconditions.checkNotNull(coordinatorId, "coordinatorId is null");
        Preconditions.checkArgument(!coordinatorId.isEmpty(), "coordinatorId is empty");
        this.coordinatorId = coordinatorId;
    }

    public void setAgentId(String agentId)
    {
        Preconditions.checkNotNull(agentId, "agentId is null");
        Preconditions.checkArgument(!agentId.isEmpty(), "agentId is empty");
        this.agentId = agentId;
    }

    public void setLocation(String location)
    {
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkArgument(!location.isEmpty(), "location is empty");
        this.location = location;
    }

    public void setInstanceType(String instanceType)
    {
        Preconditions.checkNotNull(instanceType, "instanceType is null");
        Preconditions.checkArgument(!instanceType.isEmpty(), "instanceType is empty");
        this.instanceType = instanceType;
    }

    public void setInternalIp(String internalIp)
    {
        Preconditions.checkNotNull(internalIp, "internalIp is null");
        Preconditions.checkArgument(!internalIp.isEmpty(), "internalIp is empty");
        this.internalIp = InetAddresses.forString(internalIp);
    }

    public void setExternalAddress(String externalAddress)
    {
        Preconditions.checkNotNull(externalAddress, "externalAddress is null");
        Preconditions.checkArgument(!externalAddress.isEmpty(), "externalAddress is empty");
        this.externalAddress = externalAddress;
    }

    public void setUseInternalAddress(boolean useInternalAddress)
    {
        this.useInternalAddress = useInternalAddress;
    }

    public Commander build()
            throws IOException
    {
        Preconditions.checkNotNull(coordinatorUri, "coordinatorUri is null");

        String scheme = coordinatorUri.getScheme();
        if ("http".equals(scheme)) {
            return new HttpCommander(coordinatorUri, useInternalAddress);
        }
        else if ("file".equals(scheme) || scheme == null) {
            return createLocalCommander();
        }
        throw new IllegalAccessError("Unsupported coordinator protocol " + scheme);
    }

    private LocalCommander createLocalCommander()
    {
        Preconditions.checkNotNull(coordinatorUri, "coordinatorUri is null");
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(this.repositories, "binaryRepositories is null");

        //
        // Create agent
        //
        String slotsDir = coordinatorUri.getPath();
        String agentLocation = this.location == null ? Joiner.on('/').join("", "local", agentId, "agent") : location;
        DeploymentManagerFactory deploymentManagerFactory = new DirectoryDeploymentManagerFactory(agentLocation, slotsDir, COMMAND_TIMEOUT);

        LifecycleManager lifecycleManager = new LauncherLifecycleManager(
                environment,
                internalIp,
                externalAddress,
                null,
                COMMAND_TIMEOUT,
                COMMAND_TIMEOUT,
                new File(slotsDir, "service-inventory.json").toURI());

        Agent agent = new Agent(agentId,
                agentLocation,
                slotsDir,
                FAKE_LOCAL_URI,
                FAKE_LOCAL_URI,
                null,
                deploymentManagerFactory,
                lifecycleManager,
                COMMAND_TIMEOUT);

        //
        // Create coordinator
        //
        CoordinatorConfig coordinatorConfig = new CoordinatorConfig()
                .setRepositories(repositories)
                .setDefaultRepositoryGroupId(mavenDefaultGroupIds);
        Repository repository = new RepositorySet(ImmutableSet.<Repository>of(
                new MavenRepository(coordinatorConfig),
                new HttpRepository(coordinatorConfig)));
        ServiceInventory serviceInventory = new HttpServiceInventory(repository,
                JsonCodec.listJsonCodec(ServiceDescriptor.class),
                new File(slotsDir, "service-inventory-cache"));

        Provisioner provisioner = new LocalProvisioner();

        StateManager stateManager = new InMemoryStateManager();
        for (SlotStatus slotStatus : agent.getAgentStatus().getSlotStatuses()) {
            stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), slotStatus.getState(), slotStatus.getAssignment()));
        }

        RemoteAgentFactory remoteAgentFactory = new LocalRemoteAgentFactory(agent);

        String coordinatorLocation = this.location == null ? Joiner.on('/').join("", "local", coordinatorId, "coordinator") : location;
        CoordinatorStatus coordinatorStatus = new CoordinatorStatus(coordinatorId,
                CoordinatorLifecycleState.ONLINE,
                instanceId,
                FAKE_LOCAL_URI,
                FAKE_LOCAL_URI,
                coordinatorLocation,
                instanceType);

        Coordinator coordinator = new Coordinator(coordinatorStatus,
                remoteAgentFactory,
                repository,
                provisioner,
                stateManager,
                serviceInventory,
                new Duration(100, TimeUnit.DAYS),
                true);

        return new LocalCommander(environment, new File(slotsDir), coordinator, repository, serviceInventory);
    }

    private class LocalProvisioner implements Provisioner
    {
        @Override
        public List<Instance> listCoordinators()
        {
            String coordinatorLocation = location == null ? Joiner.on('/').join("", "local", coordinatorId, "coordinator") : location;
            return ImmutableList.of(new Instance(agentId, instanceType, coordinatorLocation, FAKE_LOCAL_URI, FAKE_LOCAL_URI));
        }

        @Override
        public List<Instance> provisionCoordinators(String coordinatorConfigSpec,
                int coordinatorCount,
                String instanceType,
                String availabilityZone,
                String ami,
                String keyPair,
                String securityGroup)
        {
            throw new UnsupportedOperationException("Coordinators can not be provisioned in local mode");
        }

        @Override
        public List<Instance> listAgents()
        {
            String agentLocation = location == null ? Joiner.on('/').join("", "local", agentId, "agent") : location;
            return ImmutableList.of(new Instance(agentId, instanceType, agentLocation, FAKE_LOCAL_URI, FAKE_LOCAL_URI));
        }

        @Override
        public List<Instance> provisionAgents(String agentConfig,
                int agentCount,
                String instanceType,
                String availabilityZone,
                String ami,
                String keyPair,
                String securityGroup)
        {
            throw new UnsupportedOperationException("Agents can not be provisioned in local mode");
        }

        @Override
        public void terminateAgents(Iterable<String> instanceIds)
        {
            throw new UnsupportedOperationException("Agents can not be terminated in local mode");
        }
    }

    private class LocalRemoteAgentFactory implements RemoteAgentFactory
    {
        private final Agent agent;

        public LocalRemoteAgentFactory(Agent agent)
        {
            this.agent = agent;
        }

        @Override
        public RemoteAgent createRemoteAgent(Instance instance, AgentLifecycleState state)
        {
            Preconditions.checkNotNull(instance, "instance is null");
            return new LocalRemoteAgent(agent);
        }
    }

    private class LocalRemoteAgent implements RemoteAgent
    {
        private final Agent agent;

        public LocalRemoteAgent(Agent agent)
        {
            this.agent = agent;
        }

        @Override
        public void setInternalUri(URI uri)
        {
            Preconditions.checkArgument(FAKE_LOCAL_URI.equals(uri), "uri is not '" + FAKE_LOCAL_URI + "'");
        }

        @Override
        public SlotStatus install(Installation installation)
        {
            return agent.install(installation).changeInstanceId(instanceId);
        }

        @Override
        public AgentStatus status()
        {
            AgentStatus agentStatus = agent.getAgentStatus();
            return new AgentStatus(agentStatus.getAgentId(),
                    agentStatus.getState(),
                    instanceId,
                    agentStatus.getInternalUri(),
                    agentStatus.getExternalUri(),
                    agentStatus.getLocation(),
                    instanceType,
                    agentStatus.getSlotStatuses(),
                    agentStatus.getResources());
        }

        @Override
        public List<? extends RemoteSlot> getSlots()
        {
            ImmutableList.Builder<RemoteSlot> builder = ImmutableList.builder();
            for (Slot slot : agent.getAllSlots()) {
                builder.add(new LocalRemoteSlot(slot, instanceId));
            }
            return builder.build();
        }

        @Override
        public void updateStatus()
        {
        }

        @Override
        public void setServiceInventory(List<ServiceDescriptor> serviceInventory)
        {
        }
    }

    private static class LocalRemoteSlot implements RemoteSlot
    {
        private final Slot slot;
        private final String instanceId;

        public LocalRemoteSlot(Slot slot, String instanceId)
        {
            this.slot = slot;
            this.instanceId = instanceId;
        }

        @Override
        public UUID getId()
        {
            return slot.getId();
        }

        @Override
        public SlotStatus terminate()
        {
            return slot.terminate().changeInstanceId(instanceId);
        }

        @Override
        public SlotStatus assign(Installation installation)
        {
            return slot.assign(installation).changeInstanceId(instanceId);
        }

        @Override
        public SlotStatus status()
        {
            return slot.status().changeInstanceId(instanceId);
        }

        @Override
        public SlotStatus start()
        {
            return slot.start().changeInstanceId(instanceId);
        }

        @Override
        public SlotStatus restart()
        {
            return slot.restart().changeInstanceId(instanceId);
        }

        @Override
        public SlotStatus stop()
        {
            return slot.stop().changeInstanceId(instanceId);
        }
    }

    public static class ToUriFunction implements Function<String, URI>
    {
        public URI apply(String uri)
        {
            if (!uri.endsWith("/")) {
                uri = uri + "/";
            }
            return URI.create(uri);
        }
    }
}
