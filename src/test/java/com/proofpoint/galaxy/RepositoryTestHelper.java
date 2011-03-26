package com.proofpoint.galaxy;

import com.proofpoint.galaxy.agent.Assignment;
import com.proofpoint.galaxy.console.ConsoleAssignment;
import com.proofpoint.galaxy.console.MockBinaryRepository;
import com.proofpoint.galaxy.console.MockConfigRepository;

public class RepositoryTestHelper
{
    private static final MockBinaryRepository binaryRepository = new MockBinaryRepository();
    private static final MockConfigRepository configRepository = new MockConfigRepository();

    public static Assignment newAssignment(String binary, String config)
    {
        BinarySpec binarySpec = BinarySpec.valueOf(binary);
        ConfigSpec configSpec = ConfigSpec.valueOf(config);
        return new Assignment(binarySpec, binaryRepository, configSpec, configRepository);
    }

    public static Assignment newAssignment(ConsoleAssignment assignment)
    {
        return new Assignment(assignment.getBinary(), binaryRepository, assignment.getConfig(), configRepository);
    }
}
