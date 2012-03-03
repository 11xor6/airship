package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.RESOLVED_APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.SHORT_APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestCoordinator
{
    private Coordinator coordinator;
    private Duration statusExpiration = new Duration(500, TimeUnit.MILLISECONDS);
    private MockProvisioner provisioner;
    private TestingMavenRepository repository;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("testing");

        repository = new TestingMavenRepository();

        provisioner = new MockProvisioner();
        coordinator = new Coordinator(nodeInfo.getEnvironment(),
                provisioner.getAgentFactory(),
                repository,
                provisioner,
                new InMemoryStateManager(),
                new MockServiceInventory(),
                statusExpiration,
                false);
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repository.destroy();
    }

    @Test
    public void testNoAgents()
            throws Exception
    {
        assertTrue(coordinator.getAllAgentStatus().isEmpty());
    }

    @Test
    public void testOneAgent()
            throws Exception
    {
        String agentId = UUID.randomUUID().toString();
        URI agentUri = URI.create("fake://agent/" + agentId);

        AgentStatus status = new AgentStatus(agentId,
                AgentLifecycleState.ONLINE,
                "instance-id",
                agentUri,
                agentUri,
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.of("cpu", 8, "memory", 1024));
        provisioner.addAgent(status);
        coordinator.updateAllAgents();

        assertEquals(coordinator.getAllAgentStatus(), ImmutableList.of(status));
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);
    }

    @Test
    public void testAgentProvision()
            throws Exception
    {
        String agentId = UUID.randomUUID().toString();
        URI agentUri = URI.create("fake://agent/" + agentId);

        // provision the agent
        provisioner.addAgent(agentId, agentUri);

        // coordinator won't see it until it update is called
        assertTrue(coordinator.getAllAgentStatus().isEmpty());

        // announce the new agent and verify
        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);

        // remove the slot from provisioner
        provisioner.removeAgent(agentId);
        coordinator.updateAllAgents();
        assertNull(coordinator.getAgentStatus(agentId));
    }

    @Test
    public void testInstallWithinShortBinarySpec()
    {
        URI agentUri = URI.create("fake://appleServer1/");
        provisioner.addAgent(UUID.randomUUID().toString(), agentUri, ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgents();

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, SHORT_APPLE_ASSIGNMENT);

        assertEquals(slots.size(), 1);
        for (SlotStatus slot : slots) {
            assertAppleSlot(slot);
        }
    }

    @Test
    public void testInstallWithinResourceLimit()
    {
        URI agentUri = URI.create("fake://appleServer1/");

        provisioner.addAgent("instance-id", agentUri, ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgents();

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);

        assertEquals(slots.size(), 1);
        for (SlotStatus slot : slots) {
            assertAppleSlot(slot);
        }
    }

    @Test
    public void testInstallNotEnoughResources()
    {
        URI agentUri = URI.create("fake://appleServer1/");

        provisioner.addAgent("instance-id", agentUri);
        coordinator.updateAllAgents();

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);
        assertEquals(slots.size(), 0);
    }

    @Test
    public void testInstallResourcesConsumed()
    {
        URI agentUri = URI.create("fake://appleServer1/");
        provisioner.addAgent("instance-id", agentUri, ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgents();

        // install an apple server
        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);
        assertEquals(slots.size(), 1);
        assertAppleSlot(Iterables.get(slots, 0));

        // try to install a banana server which will fail
        slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, BANANA_ASSIGNMENT);
        assertEquals(slots.size(), 0);
    }

    private void assertAppleSlot(SlotStatus slot)
    {
        assertEquals(slot.getAssignment(), RESOLVED_APPLE_ASSIGNMENT);
        assertEquals(slot.getState(), STOPPED);
        assertEquals(slot.getResources(), ImmutableMap.of("cpu", 1, "memory", 512));
    }
}
