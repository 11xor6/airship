package io.airlift.airship.configbundler;

import java.io.IOException;
import java.io.OutputStream;

interface Generator
{
    void write(OutputStream out)
            throws IOException;
}
