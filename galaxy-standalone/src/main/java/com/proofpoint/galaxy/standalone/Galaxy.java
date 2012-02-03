package com.proofpoint.galaxy.standalone;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.NullOutputStream;
import com.proofpoint.galaxy.coordinator.AwsProvisioner;
import com.proofpoint.galaxy.coordinator.HttpRepository;
import com.proofpoint.galaxy.coordinator.Instance;
import com.proofpoint.galaxy.coordinator.MavenRepository;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.RepositorySet;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.galaxy.standalone.CommanderFactory.ToUriFunction;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;
import org.iq80.cli.Arguments;
import org.iq80.cli.Cli;
import org.iq80.cli.Cli.CliBuilder;
import org.iq80.cli.Command;
import org.iq80.cli.Help;
import org.iq80.cli.Option;
import org.iq80.cli.ParseException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RESTARTING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.standalone.Column.binary;
import static com.proofpoint.galaxy.standalone.Column.config;
import static com.proofpoint.galaxy.standalone.Column.ip;
import static com.proofpoint.galaxy.standalone.Column.location;
import static com.proofpoint.galaxy.standalone.Column.shortId;
import static com.proofpoint.galaxy.standalone.Column.status;
import static com.proofpoint.galaxy.standalone.Column.statusMessage;
import static org.iq80.cli.Cli.buildCli;

public class Galaxy
{
    private static final File CONFIG_FILE = new File(System.getProperty("user.home", "."), ".galaxyconfig");
    private static final String GALAXY_VERSION = "0.8-SNAPSHOT";

    public static void main(String[] args)
            throws Exception
    {
        CliBuilder<Callable> builder = buildCli("galaxy", Callable.class)
                .withDescription("cloud management system")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(HelpCommand.class,
                        ShowCommand.class,
                        InstallCommand.class,
                        UpgradeCommand.class,
                        TerminateCommand.class,
                        StartCommand.class,
                        StopCommand.class,
                        RestartCommand.class,
                        SshCommand.class,
                        ResetToActualCommand.class);

        builder.withGroup("agent")
                .withDescription("Manage agents")
                .withDefaultCommand(AgentShowCommand.class)
                .withCommands(AgentShowCommand.class,
                        AgentAddCommand.class,
                        AgentTerminateCommand.class);

        builder.withGroup("environment")
                .withDescription("Manage environments")
                .withDefaultCommand(Help.class)
                .withCommands(EnvironmentProvisionLocal.class,
                        EnvironmentProvisionAws.class,
                        EnvironmentUse.class,
                        EnvironmentAdd.class);

        builder.withGroup("config")
                .withDescription("Manage configuration")
                .withDefaultCommand(Help.class)
                .withCommands(ConfigGet.class,
                        ConfigGetAll.class,
                        ConfigSet.class,
                        ConfigAdd.class,
                        ConfigUnset.class);

        Cli<Callable> galaxyParser = builder.build();

        galaxyParser.parse(args).call();
    }

    public static abstract class GalaxyCommand implements Callable<Void>
    {
        @Inject
        public GlobalOptions globalOptions = new GlobalOptions();

        @Override
        public Void call()
                throws Exception
        {
            initializeLogging();

            Config config = Config.loadConfig(CONFIG_FILE);

            String ref = globalOptions.environment;
            if (ref == null) {
                ref = config.get("environment.default");
            }
            if (ref == null) {
                throw new RuntimeException("You must specify an environment.");
            }
            String environment = config.get("environment." + ref + ".name");
            if (environment == null) {
                throw new RuntimeException("Unknown environment " + ref);
            }
            String coordinator = config.get("environment." + ref + ".coordinator");
            if (coordinator == null) {
                throw new RuntimeException("Environment " + ref + " does not have a coordinator url.  You can add a coordinator url with galaxy coordinator add <url>");
            }

            URI coordinatorUri = new URI(coordinator);

            Commander commander = new CommanderFactory()
                    .setEnvironment(environment)
                    .setCoordinatorUri(coordinatorUri)
                    .setRepositories(config.getAll("environment." + ref + ".repository"))
                    .setMavenDefaultGroupIds(config.getAll("environment." + ref + ".maven-group-id"))
                    .build();

            try {
                execute(commander);
            }
            catch (Exception e) {
                if (globalOptions.debug) {
                    throw e;
                }
                else {
                    System.out.println(firstNonNull(e.getMessage(), "Unknown error"));
                }
            }

            return null;
        }

        private void initializeLogging()
                throws IOException
        {
            // unhook out and err while initializing logging or logger will print to them
            PrintStream out = System.out;
            PrintStream err = System.err;
            try {
                if (globalOptions.debug) {
                    Logging logging = new Logging();
                    logging.initialize(new LoggingConfiguration());
                }
                else {
                    System.setOut(new PrintStream(new NullOutputStream()));
                    System.setErr(new PrintStream(new NullOutputStream()));

                    Logging logging = new Logging();
                    logging.initialize(new LoggingConfiguration());
                    logging.disableConsole();
                }
            }
            finally {
                System.setOut(out);
                System.setErr(err);
            }
        }

        public abstract void execute(Commander commander)
                throws Exception;

        public void displaySlots(Iterable<Record> slots)
        {
            if (Iterables.isEmpty(slots)) {
                System.out.println("No slots match the provided filters.");
            }
            else {
                TablePrinter tablePrinter = new TablePrinter(shortId, ip, status, binary, config, statusMessage);
                tablePrinter.print(slots);
            }
        }

        public void displayAgents(Iterable<Record> agents)
        {
            if (Iterables.isEmpty(agents)) {
                System.out.println("No agents match the provided filters.");
            }
            else {
                TablePrinter tablePrinter = new TablePrinter(shortId, ip, status, Column.instanceType, location);
                tablePrinter.print(agents);
            }
        }
    }

    @Command(name = "help", description = "Display help information about galaxy")
    public static class HelpCommand extends GalaxyCommand
    {
        @Inject
        public Help help;

        @Override
        public Void call()
                throws Exception
        {
            help.call();
            return null;
        }

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("HelpCommand");
            sb.append("{help=").append(help);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "show", description = "Show state of all slots")
    public static class ShowCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.show(slotFilter);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("ShowCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "install", description = "Install software in a new slot")
    public static class InstallCommand extends GalaxyCommand
    {
        @Option(name = {"--count"}, description = "Number of instances to install")
        public int count = 1;

        @Inject
        public final AgentFilter agentFilter = new AgentFilter();

        @Arguments(usage = "<groupId:artifactId[:packaging[:classifier]]:version> @<component:pools:version>",
                description = "The binary and @configuration to install.  The default packaging is tar.gz")
        public final List<String> assignment = Lists.newArrayList();

        @Override
        public void execute(Commander commander)
        {
            if (assignment.size() != 2) {
                throw new ParseException("You must specify a binary and config to install.");
            }
            String binary;
            String config;
            if (assignment.get(0).startsWith("@")) {
                config = assignment.get(0);
                binary = assignment.get(1);
            }
            else {
                binary = assignment.get(0);
                config = assignment.get(1);
            }

            Assignment assignment = new Assignment(binary, config);
            List<Record> slots = commander.install(agentFilter, count, assignment);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("InstallCommand");
            sb.append("{count=").append(count);
            sb.append(", agentFilter=").append(agentFilter);
            sb.append(", assignment=").append(assignment);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "upgrade", description = "Upgrade software in a slot")
    public static class UpgradeCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Arguments(usage = "[<binary-version>] [@<config-version>]",
                description = "Version of the binary and/or @configuration")
        public final List<String> versions = Lists.newArrayList();

        @Override
        public void execute(Commander commander)
        {
            if (versions.size() != 1 && versions.size() != 2) {
                throw new ParseException("You must specify a binary version or a config version for upgrade.");
            }

            String binaryVersion = null;
            String configVersion = null;
            if (versions.get(0).startsWith("@")) {
                configVersion = versions.get(0);
                if (versions.size() > 1) {
                    binaryVersion = versions.get(1);
                }
            }
            else {
                binaryVersion = versions.get(0);
                if (versions.size() > 1) {
                    configVersion = versions.get(1);
                }
            }

            UpgradeVersions upgradeVersions = new UpgradeVersions(binaryVersion, configVersion);
            List<Record> slots = commander.upgrade(slotFilter, upgradeVersions);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("UpgradeCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", versions=").append(versions);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "terminate", description = "Terminate (remove) a slot")
    public static class TerminateCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.terminate(slotFilter);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("TerminateCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "start", description = "Start a server")
    public static class StartCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, RUNNING);
            displaySlots(slots);
        }
    }

    @Command(name = "stop", description = "Stop a server")
    public static class StopCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, STOPPED);
            displaySlots(slots);
        }
    }

    @Command(name = "restart", description = "Restart server")
    public static class RestartCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, RESTARTING);
            displaySlots(slots);
        }
    }

    @Command(name = "reset-to-actual", description = "Reset slot expected state to actual")
    public static class ResetToActualCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.resetExpectedState(slotFilter);
            displaySlots(slots);
        }
    }

    @Command(name = "ssh", description = "ssh to slot installation")
    public static class SshCommand extends GalaxyCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Arguments(description = "Command to execute on the remote host")
        public String command;

        @Override
        public void execute(Commander commander)
        {
            commander.ssh(slotFilter, command);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("InstallCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", args=").append(command);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "show", description = "Show agent details")
    public static class AgentShowCommand extends GalaxyCommand
    {
        @Inject
        public final AgentFilter agentFilter = new AgentFilter();

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<Record> agents = commander.showAgents(agentFilter);
            displayAgents(agents);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentShowCommand");
            sb.append("{globalOptions=").append(globalOptions);
            sb.append(", agentFilter=").append(agentFilter);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "provision", description = "Provision a new agent")
    public static class AgentAddCommand extends GalaxyCommand
    {
        @Option(name = {"--count"}, description = "Number of agents to provision")
        public int count = 1;

        @Option(name = {"--availability-zone"}, description = "Availability zone to provision")
        public String availabilityZone;

        @Arguments(usage = "[<instance-type>]", description = "Instance type to provision")
        public String instanceType;

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<Record> agents = commander.addAgents(count, availabilityZone, instanceType);
            displayAgents(agents);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentAddCommand");
            sb.append("{count=").append(count);
            sb.append(", availabilityZone='").append(availabilityZone).append('\'');
            sb.append(", instanceType=").append(instanceType);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "terminate", description = "Terminate an agent")
    public static class AgentTerminateCommand extends GalaxyCommand
    {
        @Arguments(title = "agent-id", description = "Agent to terminate", required = true)
        public String agentId;

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            Record agent = commander.terminateAgent(agentId);
            displayAgents(ImmutableList.of(agent));
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentTerminateCommand");
            sb.append("{agentId='").append(agentId).append('\'');
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "provision-local", description = "Provision a local environment")
    public static class EnvironmentProvisionLocal implements Callable<Void>
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Option(name = "--repository", description = "Repository for binaries and configurations")
        public final List<String> repository = newArrayList();

        @Option(name = "--maven-default-group-id", description = "Default maven group-id")
        public final List<String> mavenDefaultGroupId = newArrayList();

        @Arguments(usage = "<ref> <path>",
                description = "Reference name and path for the environment")
        public List<String> args;

        @Override
        public Void call()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a name and path.");
            }

            String ref = args.get(0);
            String path = args.get(1);
            if (environment == null) {
                environment = ref;
            }

            String nameProperty = "environment." + ref + ".name";
            String coordinatorProperty = "environment." + ref + ".coordinator";
            String repositoryProperty = "environment." + ref + ".repository";
            String mavenGroupIdProperty = "environment." + ref + ".maven-group-id";

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }
            config.set(nameProperty, environment);
            config.set(coordinatorProperty, path);
            for (String repo : repository) {
                config.add(repositoryProperty, repo);
            }
            for (String groupId : mavenDefaultGroupId) {
                config.add(mavenGroupIdProperty, groupId);
            }
            if (config.get("environment.default") == null) {
                config.set("environment.default", ref);
            }
            config.save(CONFIG_FILE);
            return null;
        }
    }

    @Command(name = "provision-aws", description = "Provision an AWS environment")
    public static class EnvironmentProvisionAws implements Callable<Void>
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Option(name = "--aws-endpoint", description = "Amazon endpoint URL")
        public String awsEndpoint;

        @Option(name = "--ami", description = "Amazon Machine Image for EC2 instances")
        public String ami = "ami-27b7744e";

        @Option(name = "--key-pair", description = "Key pair for all EC2 instances")
        public String keyPair = "keypair";

        @Option(name = "--security-group", description = "Security group for all EC2 instances")
        public String securityGroup = "default";

        @Option(name = "--availability-zone", description = "EC2 availability zone for coordinator")
        public String availabilityZone;

        @Option(name = "--instance-type", description = "EC2 instance type for coordinator")
        public String instanceType = "t1.micro";

        @Option(name = "--coordinator-config", description = "Configuration for the coordinator")
        public String coordinatorConfig;



        @Option(name = "--repository", description = "Repository for binaries and configurations")
        public final List<String> repository = newArrayList();

        @Option(name = "--maven-default-group-id", description = "Default maven group-id")
        public final List<String> mavenDefaultGroupId = newArrayList();

        @Option(name = "--port", description = "Port for coordinator")
        public int port = 64000;


        @Arguments(description = "Reference name for the environment", required = true)
        public String ref;

        @Override
        public Void call()
                throws Exception
        {
            Preconditions.checkNotNull(ref, "You must specify a name");

            if (environment == null) {
                environment = ref;
            }

            String nameProperty = "environment." + ref + ".name";

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }

            String accessKey = config.get("aws.access-key");
            Preconditions.checkNotNull(accessKey, "You must set the aws access-key with: galaxy config set aws.access-key <key>");
            String secretKey = config.get("aws.secret-key");
            Preconditions.checkNotNull(secretKey, "You must set the aws secret-key with: galaxy config set aws.secret-key <key>");


            List<URI> repoBases = ImmutableList.copyOf(Lists.transform(repository, new ToUriFunction()));
            Repository repository = new RepositorySet(ImmutableSet.<Repository>of(
                    new MavenRepository(mavenDefaultGroupId, repoBases),
                    new HttpRepository(repoBases, null, null, null)));

            AmazonEC2Client ec2Client = new AmazonEC2Client(new BasicAWSCredentials(accessKey, secretKey));
            if (awsEndpoint != null) {
                ec2Client.setEndpoint(awsEndpoint);
            }

            AwsProvisioner provisioner = new AwsProvisioner(ec2Client,
                    environment,
                    GALAXY_VERSION,
                    this.repository,
                    awsEndpoint,
                    accessKey,
                    secretKey,
                    ami,
                    keyPair,
                    securityGroup,
                    instanceType,
                    port,
                    ami,
                    keyPair,
                    securityGroup,
                    instanceType,
                    port,
                    repository);

            List<Instance> instances = provisioner.provisionCoordinator(instanceType, availabilityZone);

            config.set(nameProperty, environment);

            String coordinatorProperty = "environment." + ref + ".coordinator";
            for (Instance instance : instances) {
//                config.set(coordinatorProperty, instance.getUri().toASCIIString());
            }

            if (config.get("environment.default") == null) {
                config.set("environment.default", ref);
            }
            config.save(CONFIG_FILE);
            return null;
        }
    }

    @Command(name = "add", description = "Add an environment")
    public static class EnvironmentAdd implements Callable<Void>
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Arguments(usage = "<ref> <coordinator-url>",
                description = "Reference name and a coordinator url for the environment")
        public List<String> args;

        @Override
        public Void call()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify an environment and a coordinator URL.");
            }

            String ref = args.get(0);
            String coordinatorUrl = args.get(1);

            if (environment == null) {
                environment = ref;
            }

            String nameProperty = "environment." + ref + ".name";

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }

            config.set(nameProperty, environment);
            config.set("environment." + ref + ".coordinator", coordinatorUrl);
            config.save(CONFIG_FILE);
            return null;
        }
    }

    @Command(name = "use", description = "Set the default environment")
    public static class EnvironmentUse implements Callable<Void>
    {
        @Arguments(description = "Environment to make the default")
        public String ref;

        @Override
        public Void call()
                throws Exception
        {
            Preconditions.checkNotNull(ref, "You must specify an environment");

            String nameProperty = "environment." + ref + ".name";

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) == null) {
                throw new IllegalArgumentException("Unknown environment " + ref);
            }
            config.set("environment.default", ref);
            config.save(CONFIG_FILE);
            return null;
        }
    }

    @Command(name = "get", description = "Get a configuration value")
    public static class ConfigGet implements Callable<Void>
    {
        @Arguments(description = "Key to get")
        public String key;

        @Override
        public Void call()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            Config config = Config.loadConfig(CONFIG_FILE);
            List<String> values = config.getAll(key);
            Preconditions.checkArgument(values.size() < 2, "More than one value for the key %s", key);
            if (!values.isEmpty()) {
                System.out.println(values.get(0));
            }
            return null;
        }
    }

    @Command(name = "get-all", description = "Get all values of configuration")
    public static class ConfigGetAll implements Callable<Void>
    {
        @Arguments(description = "Key to get")
        public String key;

        @Override
        public Void call()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            Config config = Config.loadConfig(CONFIG_FILE);
            List<String> values = config.getAll(key);
            for (String value : values) {
                System.out.println(value);
            }
            return null;
        }
    }

    @Command(name = "set", description = "Set a configuration value")
    public static class ConfigSet implements Callable<Void>
    {
        @Arguments(usage = "<key> <value>",
                description = "Key-value pair to set")
        public List<String> args;

        @Override
        public Void call()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a key and a value.");
            }

            String key = args.get(0);
            String value = args.get(1);

            Config config = Config.loadConfig(CONFIG_FILE);
            config.set(key, value);
            config.save(CONFIG_FILE);
            return null;
        }
    }

    @Command(name = "add", description = "Add a configuration value")
    public static class ConfigAdd implements Callable<Void>
    {
        @Arguments(usage = "<key> <value>",
                description = "Key-value pair to add")
        public List<String> args;

        @Override
        public Void call()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a key and a value.");
            }

            String key = args.get(0);
            String value = args.get(1);

            Config config = Config.loadConfig(CONFIG_FILE);
            config.add(key, value);
            config.save(CONFIG_FILE);
            return null;
        }
    }

    @Command(name = "unset", description = "Unset a configuration value")
    public static class ConfigUnset implements Callable<Void>
    {
        @Arguments(description = "Key to unset")
        public String key;

        @Override
        public Void call()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            Config config = Config.loadConfig(CONFIG_FILE);
            config.unset(key);
            config.save(CONFIG_FILE);
            return null;
        }
    }
}
