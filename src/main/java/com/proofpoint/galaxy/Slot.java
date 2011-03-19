package com.proofpoint.galaxy;

import java.net.URI;
import java.util.UUID;

public interface Slot
{
    UUID getId();

    String getName();

    URI getSelf();

    SlotStatus assign(Assignment assignment);

    SlotStatus clear();

    SlotStatus status();

    SlotStatus start();

    SlotStatus restart();

    SlotStatus stop();
}
