package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.proofpoint.galaxy.coordinator.MavenMetadata.SnapshotVersion;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.MavenCoordinates;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.BinarySpec.createBinarySpec;

public class MavenRepository implements Repository
{
    private static final Pattern TIMESTAMP_VERSION = Pattern.compile("^(.+)-[0-9]{8}\\.[0-9]{6}\\-[0-9]$");
    private final List<String> defaultGroupIds;
    private final List<URI> repositoryBases;

    public MavenRepository(Iterable<String> defaultGroupIds, URI repositoryBase, URI... repositoryBases)
    {
        this.defaultGroupIds = ImmutableList.copyOf(defaultGroupIds);
        this.repositoryBases = ImmutableList.<URI>builder().add(repositoryBase).add(repositoryBases).build();
    }

    public MavenRepository(Iterable<String> defaultGroupIds, Iterable<URI> repositoryBases)
    {
        this.defaultGroupIds = ImmutableList.copyOf(defaultGroupIds);
        this.repositoryBases = ImmutableList.copyOf(repositoryBases);
    }

    @Inject
    public MavenRepository(CoordinatorConfig config)
            throws Exception
    {
        this.defaultGroupIds = ImmutableList.copyOf(Splitter.on(",").trimResults().omitEmptyStrings().split(config.getDefaultRepositoryGroupId()));

        Builder<URI> builder = ImmutableList.builder();

        for (String binaryRepoBase : config.getBinaryRepoBases()) {
            if (!binaryRepoBase.endsWith("/")) {
                binaryRepoBase = binaryRepoBase + "/";
            }
            builder.add(URI.create(binaryRepoBase));
        }
        repositoryBases = builder.build();
    }

    @Override
    public URI getUri(MavenCoordinates binarySpec)
    {
        return getBinaryUri(binarySpec, true);
    }

    public URI getBinaryUri(MavenCoordinates binarySpec, boolean required)
    {
        // resolve binary spec groupId or snapshot version
        binarySpec = resolve(binarySpec);

        List<URI> checkedUris = newArrayList();
        for (URI repositoryBase : repositoryBases) {
            // build the uri
            StringBuilder builder = new StringBuilder();
            builder.append(binarySpec.getGroupId().replace('.', '/')).append('/');
            builder.append(binarySpec.getArtifactId()).append('/');
            builder.append(binarySpec.getVersion()).append('/');
            builder.append(binarySpec.getArtifactId()).append('-').append(binarySpec.getFileVersion());
            if (binarySpec.getClassifier() != null) {
                builder.append('-').append(binarySpec.getClassifier());
            }
            builder.append('.').append(binarySpec.getPackaging());

            URI uri = repositoryBase.resolve(builder.toString());

            // try to download some of the file
            if (isValidBinary(uri)) {
                return uri;
            }

            checkedUris.add(uri);
        }
        if (required) {
            throw new RuntimeException("Unable to find binary " + binarySpec + " at " + checkedUris);
        }
        else {
            return null;
        }
    }

    @Override
    public MavenCoordinates resolve(MavenCoordinates binarySpec)
    {
        if (binarySpec.isResolved()) {
            return binarySpec;
        }

        List<String> groupIds;
        if (binarySpec.getGroupId() != null) {
            groupIds = ImmutableList.of(binarySpec.getGroupId());
        }
        else {
            groupIds = defaultGroupIds;
        }

        List<MavenCoordinates> binarySpecs = newArrayList();
        for (String groupId : groupIds) {
            // check for a file with the exact name
            MavenCoordinates resolvedSpec = createBinarySpec(groupId,
                    binarySpec.getArtifactId(),
                    binarySpec.getVersion(),
                    binarySpec.getPackaging(),
                    binarySpec.getClassifier(),
                    binarySpec.getFileVersion());

            if (getBinaryUri(resolvedSpec, false) != null) {
                binarySpecs.add(resolvedSpec);
                continue;
            }

            // check of a timestamped snapshot file
            if (binarySpec.getVersion().contains("SNAPSHOT")) {
                MavenCoordinates timestampSpec = resolveSnapshotTimestamp(binarySpec, groupId);
                if (timestampSpec != null) {
                    binarySpecs.add(timestampSpec);
                    continue;
                }
            }

            // Snapshot revisions are resolved to timestamp version which may need to be converted back to SNAPSHOT for resolution
            Matcher timestampMatcher = TIMESTAMP_VERSION.matcher(binarySpec.getVersion());
            if (timestampMatcher.matches()) {
                MavenCoordinates snapshotSpec = createBinarySpec(groupId,
                        binarySpec.getArtifactId(),
                        timestampMatcher.group(1) + "-SNAPSHOT",
                        binarySpec.getPackaging(),
                        binarySpec.getClassifier(),
                        binarySpec.getVersion());

                if (getBinaryUri(snapshotSpec, false) != null) {
                    binarySpecs.add(snapshotSpec);
                }
            }
        }

        if (binarySpecs.size() > 1) {
            throw new RuntimeException("Ambiguous spec " + binarySpec + "  matched " + binarySpecs);
        }

        if (binarySpecs.isEmpty()) {
            throw new RuntimeException("Unable to find " + binarySpec + " at " + repositoryBases);
        }

        return binarySpecs.get(0);
    }

    private MavenCoordinates resolveSnapshotTimestamp(MavenCoordinates binarySpec, String groupId)
    {

        for (URI repositoryBase : repositoryBases) {
            try {
                // load maven metadata file
                StringBuilder builder = new StringBuilder();
                builder.append(groupId.replace('.', '/')).append('/');
                builder.append(binarySpec.getArtifactId()).append('/');
                builder.append(binarySpec.getVersion()).append('/');
                builder.append("maven-metadata.xml");
                URI uri = repositoryBase.resolve(builder.toString());
                MavenMetadata metadata = MavenMetadata.unmarshalMavenMetadata(Resources.toString(uri.toURL(), Charsets.UTF_8));

                for (SnapshotVersion snapshotVersion : metadata.versioning.snapshotVersions) {
                    if (binarySpec.getPackaging().equals(snapshotVersion.extension) && Objects.equal(binarySpec.getClassifier(), snapshotVersion.classifier)) {
                        MavenCoordinates timestampSpec = createBinarySpec(groupId,
                                binarySpec.getArtifactId(),
                                binarySpec.getVersion(),
                                binarySpec.getPackaging(),
                                binarySpec.getClassifier(),
                                snapshotVersion.value);

                        return timestampSpec;
                    }
                }
            }
            catch (Exception ignored) {
                // no maven-metadata.xml file... hope this is laid out normally
            }
        }
        return null;
    }

    private boolean isValidBinary(URI uri)
    {
        try {
            InputSupplier<InputStream> inputSupplier = Resources.newInputStreamSupplier(uri.toURL());
            ByteStreams.readBytes(inputSupplier, new ByteProcessor<Void>()
            {
                private int count;

                public boolean processBytes(byte[] buffer, int offset, int length)
                {
                    count += length;
                    // make sure we got at least 10 bytes
                    return count < 10;
                }

                public Void getResult()
                {
                    return null;
                }
            });
            return true;
        }
        catch (Exception ignored) {
        }
        return false;
    }
}
