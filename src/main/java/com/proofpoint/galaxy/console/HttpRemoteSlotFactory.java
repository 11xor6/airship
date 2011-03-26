package com.proofpoint.galaxy.console;

import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.SlotStatus;

public class HttpRemoteSlotFactory implements RemoteSlotFactory
{
    private final AsyncHttpClient httpClient;

    @Inject
    public HttpRemoteSlotFactory()
    {
        httpClient = new AsyncHttpClient();
    }

    public HttpRemoteSlotFactory(AsyncHttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    @Override
    public RemoteSlot createRemoteSlot(SlotStatus slotStatus)
    {
        return new HttpRemoteSlot(slotStatus, httpClient);
    }
}
