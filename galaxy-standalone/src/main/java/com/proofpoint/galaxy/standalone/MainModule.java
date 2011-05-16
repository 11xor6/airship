package com.proofpoint.galaxy.standalone;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.AgentConfig;
import com.proofpoint.galaxy.agent.AnnouncementService;
import com.proofpoint.galaxy.agent.AssignmentResource;
import com.proofpoint.galaxy.agent.DeploymentManagerFactory;
import com.proofpoint.galaxy.agent.DirectoryDeploymentManagerFactory;
import com.proofpoint.galaxy.agent.LauncherLifecycleManager;
import com.proofpoint.galaxy.agent.LifecycleManager;
import com.proofpoint.galaxy.agent.LifecycleResource;
import com.proofpoint.galaxy.agent.SlotResource;
import com.proofpoint.galaxy.coordinator.AdminResource;
import com.proofpoint.galaxy.coordinator.AnnounceResource;
import com.proofpoint.galaxy.coordinator.BinaryRepository;
import com.proofpoint.galaxy.coordinator.ConfigRepository;
import com.proofpoint.galaxy.coordinator.ConfigResource;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.CoordinatorAssignmentResource;
import com.proofpoint.galaxy.coordinator.CoordinatorConfig;
import com.proofpoint.galaxy.coordinator.CoordinatorLifecycleResource;
import com.proofpoint.galaxy.coordinator.CoordinatorSlotResource;
import com.proofpoint.galaxy.coordinator.GitConfigRepository;
import com.proofpoint.galaxy.coordinator.GitConfigRepositoryConfig;
import com.proofpoint.galaxy.coordinator.GitConfigResource;
import com.proofpoint.galaxy.coordinator.HttpRemoteSlotFactory;
import com.proofpoint.galaxy.coordinator.InvalidSlotFilterExceptionMapper;
import com.proofpoint.galaxy.coordinator.LocalConfigRepository;
import com.proofpoint.galaxy.coordinator.MavenBinaryRepository;
import com.proofpoint.galaxy.coordinator.RemoteSlotFactory;
import com.proofpoint.galaxy.coordinator.SimpleConfigRepository;
import org.weakref.jmx.guice.MBeanModule;

public class MainModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.bind(Coordinator.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorSlotResource.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorAssignmentResource.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorLifecycleResource.class).in(Scopes.SINGLETON);
        binder.bind(InvalidSlotFilterExceptionMapper.class).in(Scopes.SINGLETON);
        binder.bind(AnnounceResource.class).in(Scopes.SINGLETON);
        binder.bind(AdminResource.class).in(Scopes.SINGLETON);
        binder.bind(RemoteSlotFactory.class).to(HttpRemoteSlotFactory.class).in(Scopes.SINGLETON);
        binder.bind(BinaryRepository.class).to(MavenBinaryRepository.class).in(Scopes.SINGLETON);
        binder.bind(ConfigRepository.class).to(SimpleConfigRepository.class).in(Scopes.SINGLETON);

        binder.bind(ConfigResource.class).in(Scopes.SINGLETON);
        binder.bind(LocalConfigRepository.class).in(Scopes.SINGLETON);

        ConfigurationModule.bindConfig(binder).to(GitConfigRepositoryConfig.class);
        binder.bind(GitConfigRepository.class).in(Scopes.SINGLETON);
        binder.bind(GitConfigResource.class).in(Scopes.SINGLETON);

        ConfigurationModule.bindConfig(binder).to(CoordinatorConfig.class);


        binder.bind(Agent.class).in(Scopes.SINGLETON);
        MBeanModule.newExporter(binder).export(Agent.class).withGeneratedName();

        binder.bind(SlotResource.class).in(Scopes.SINGLETON);
        binder.bind(AssignmentResource.class).in(Scopes.SINGLETON);
        binder.bind(LifecycleResource.class).in(Scopes.SINGLETON);

        binder.bind(DeploymentManagerFactory.class).to(DirectoryDeploymentManagerFactory.class).in(Scopes.SINGLETON);
        binder.bind(LifecycleManager.class).to(LauncherLifecycleManager.class).in(Scopes.SINGLETON);

        binder.bind(AnnouncementService.class).in(Scopes.SINGLETON);

        ConfigurationModule.bindConfig(binder).to(AgentConfig.class);

    }
}
