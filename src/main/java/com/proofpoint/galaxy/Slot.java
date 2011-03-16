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
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Charsets.UTF_8;

public class Slot
{
    private static final Logger log = Logger.get(Slot.class);

    private final UUID id;
    private final String name;
    private final Duration lockWait;
    private final DeploymentManager deploymentManager;
    private final LifecycleManager lifecycleManager;

    private final ReentrantLock lock = new ReentrantLock();


    @Inject
    public Slot(String name, AgentConfig config, DeploymentManager deploymentManager, LifecycleManager lifecycleManager)
    {
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(deploymentManager, "deploymentManager is null");
        Preconditions.checkNotNull(lifecycleManager, "lifecycleManager is null");

        this.name = name;
        this.deploymentManager = deploymentManager;
        this.lifecycleManager = lifecycleManager;

        lockWait = config.getMaxLockWait();
        id = deploymentManager.getSlotId();
    }

    public UUID getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public SlotStatus assign(Assignment assignment)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");

        lock();
        try {
            log.info("Becoming %s with %s", assignment.getBinary(), assignment.getConfig());

            // deploy new server
            Deployment deployment = deploymentManager.install(assignment);

            // stop current server
            Deployment activeDeployment = deploymentManager.getActiveDeployment();
            if (activeDeployment != null) {
                LifecycleState state = lifecycleManager.stop(activeDeployment);
                if (state != LifecycleState.STOPPED) {
                    // todo error
                }
            }

            // make new server active
            deploymentManager.activate(deployment.getDeploymentId());

            // inform everyone else of the change
            // todo should this be done after the lock is released
            // @event_dispatcher.dispatch_become_success_event status
            // announce
            return new SlotStatus(id, name, assignment.getBinary(), assignment.getConfig(), LifecycleState.STOPPED);
        }
        finally {
            lock.unlock();
        }
    }

    public SlotStatus clear()
    {
        lock();
        try {
            Deployment activeDeployment = deploymentManager.getActiveDeployment();
            if (activeDeployment == null) {
                return new SlotStatus(id, name);
            }

            // Stop server
            LifecycleState state = lifecycleManager.stop(activeDeployment);
            if (state != LifecycleState.STOPPED) {
                // todo error
            }

            // remove deployment
            deploymentManager.remove(activeDeployment.getDeploymentId());
            return new SlotStatus(id, name);
        }
        finally {
            lock.unlock();
        }
    }

    public SlotStatus status()
    {
        lock();
        try {
            Deployment activeDeployment = deploymentManager.getActiveDeployment();
            if (activeDeployment == null) {
                return new SlotStatus(id, name);
            }

            LifecycleState state = lifecycleManager.status(activeDeployment);
            return new SlotStatus(id, name, activeDeployment.getAssignment().getBinary(), activeDeployment.getAssignment().getConfig(), state);
        }
        finally {
            lock.unlock();
        }
    }

    public SlotStatus start()
    {
        lock();
        try {
            Deployment activeDeployment = deploymentManager.getActiveDeployment();
            if (activeDeployment == null) {
                throw new IllegalStateException("Slot can not be started because the slot is not assigned");
            }
            LifecycleState state = lifecycleManager.start(activeDeployment);
            return new SlotStatus(id, name, activeDeployment.getAssignment().getBinary(), activeDeployment.getAssignment().getConfig(), state);
        }
        finally {
            lock.unlock();
        }
    }

    public SlotStatus restart()
    {
        lock();
        try {
            Deployment activeDeployment = deploymentManager.getActiveDeployment();
            if (activeDeployment == null) {
                throw new IllegalStateException("Slot can not be restarted because the slot is not assigned");
            }
            LifecycleState state = lifecycleManager.restart(activeDeployment);
            return new SlotStatus(id, name, activeDeployment.getAssignment().getBinary(), activeDeployment.getAssignment().getConfig(), state);
        }
        finally {
            lock.unlock();
        }
    }

    public SlotStatus stop()
    {
        lock();
        try {
            Deployment activeDeployment = deploymentManager.getActiveDeployment();
            if (activeDeployment == null) {
                throw new IllegalStateException("Slot can not be stopped because the slot is not assigned");
            }
            LifecycleState state = lifecycleManager.stop(activeDeployment);
            return new SlotStatus(id, name, activeDeployment.getAssignment().getBinary(), activeDeployment.getAssignment().getConfig(), state);
        }
        finally {
            lock.unlock();
        }
    }

    private void lock()
    {
        try {
            lock.tryLock((long) lockWait.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
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

        Slot slot = (Slot) o;

        if (!id.equals(slot.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Slot");
        sb.append("{slotId=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
