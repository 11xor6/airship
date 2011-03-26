package com.proofpoint.galaxy;

import org.testng.annotations.Test;

import java.io.File;

import static com.proofpoint.galaxy.DeploymentUtils.createSymbolicLink;
import static com.proofpoint.galaxy.DeploymentUtils.createTempDir;
import static com.proofpoint.galaxy.DeploymentUtils.isSymbolicLink;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DeploymentUtilsTest
{
    @Test
    public void testIsSymbolicLink()
            throws Exception
    {
        File baseDir = null;
        File realDir = null;
        File realFile = null;
        File symFile = null;
        File symDir = null;
        File nestDir = null;
        File nestedSymDir = null;
        try {
            baseDir = createTempDir("link-test");

            realDir = new File(baseDir, "foo");
            realDir.mkdirs();
            assertFalse(isSymbolicLink(realDir));

            realFile = new File(realDir, "realFile");
            realFile.createNewFile();
            assertFalse(isSymbolicLink(realFile));

            symFile = new File(realDir, "symFile");
            createSymbolicLink(realFile, symFile);
            assertTrue(isSymbolicLink(symFile));

            symDir = new File(baseDir, "bar");
            createSymbolicLink(realDir, symDir);
            assertTrue(isSymbolicLink(symDir));
            assertTrue(isSymbolicLink(new File(symDir, "symFile")));
            assertFalse(isSymbolicLink(new File(symDir, "realFile")));

            nestDir = new File(realDir, "nestDir");
            nestedSymDir = new File(nestDir, realDir.getName());
            createSymbolicLink(realDir, nestedSymDir);
            assertTrue(isSymbolicLink(nestedSymDir));
            assertTrue(isSymbolicLink(new File(nestedSymDir, "symFile")));
            assertFalse(isSymbolicLink(new File(nestedSymDir, "realFile")));
        }
        finally {
            delete(nestedSymDir);
            delete(nestDir);
            delete(symDir);
            delete(symFile);
            delete(realFile);
            delete(realDir);
            delete(baseDir);
        }
    }

    private void delete(File file)
    {
        if (file != null) {
            file.delete();
        }
    }
}
