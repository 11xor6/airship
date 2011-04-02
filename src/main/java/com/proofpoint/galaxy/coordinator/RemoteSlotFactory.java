package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.SlotStatus;

public interface RemoteSlotFactory
{
    RemoteSlot createRemoteSlot(SlotStatus slotStatus);
}
