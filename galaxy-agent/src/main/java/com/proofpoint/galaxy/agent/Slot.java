package com.proofpoint.galaxy.agent;

import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.Installation;

import java.net.URI;
import java.util.UUID;

public interface Slot
{
    UUID getId();

    String getName();

    URI getSelf();

    SlotStatus terminate();

    SlotStatus assign(Installation installation);

    SlotStatus getLastSlotStatus();

    SlotStatus status();

    SlotStatus start();

    SlotStatus restart();

    SlotStatus stop();
}
