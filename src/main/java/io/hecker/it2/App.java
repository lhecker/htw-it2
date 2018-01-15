package io.hecker.it2;

import com.google.common.net.HostAndPort;
import com.google.common.net.HostSpecifier;
import io.hecker.rtp.RtpSender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

import java.net.InetSocketAddress;

@SuppressWarnings("FieldMayBeFinal")
@Command(name = "it2", showDefaultValues = true)
class App {
    private static final int DEFAULT_PORT = 8554;

    @Option(names = {"-a", "--address"}, paramLabel = "<ip:port>", description = "The address to connect to")
    private InetSocketAddress m_address = new InetSocketAddress("127.0.0.1", DEFAULT_PORT);
    @Option(names = {"-s", "--server"})
    private boolean m_server = false;
    @Option(names = {"-l", "--loss"}, paramLabel = "<loss>", description = "The artificial packet loss to add (within [0,1])")
    private double m_loss = 0;
    @Option(names = {"-f", "--fec"}, paramLabel = "<size>", description = "Enable FEC with the given payload size (within [2,16])")
    private int m_fec = 0;
    @Option(names = "-v", description = "-v, -vv, -vvv, or -vvvv for INFO, DEBUG, TRACE or ALL logging level")
    private boolean[] m_verbosity = {};
    @Option(names = {"-V", "--version"}, versionHelp = true, description = "Display version info")
    private boolean m_versionInfoRequested = false;
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
    private boolean m_usageHelpRequested = false;

    public static void main(String... args) throws Exception {
        App app = new App();

        CommandLine cli = new CommandLine(app);
        cli.registerConverter(InetSocketAddress.class, new InetSocketAddressConverter());

        try {
            cli.parse(args);
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            cli.usage(System.err);
            System.exit(1);
            return;
        }

        if (app.m_versionInfoRequested) {
            cli.printVersionHelp(System.out);
            return;
        }
        if (app.m_usageHelpRequested) {
            cli.usage(System.out);
            return;
        }

        Configurator.initialize(app.createLoggingConfiguration());

        RtpSender.setSimulatedLossRate(app.m_loss);
        RtpSender.setFecSize(app.m_fec);

        if (app.m_server) {
            Server server = new Server(app.m_address);
            server.startAsync();
            server.awaitTerminated();
        } else {
            new ClientFrame(app.m_address);
        }
    }

    private BuiltConfiguration createLoggingConfiguration() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.WARN);
        builder.add(
            builder
                .newAppender("Stdout", "CONSOLE")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT)
                .add(
                    builder
                        .newLayout("PatternLayout")
                        .addAttribute("pattern", "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
                )
        );
        builder.add(
            builder
                .newRootLogger(loggingLevel())
                .add(builder.newAppenderRef("Stdout"))
        );
        return builder.build();
    }

    private Level loggingLevel() {
        switch (m_verbosity.length) {
            case 1:
                return Level.INFO;
            case 2:
                return Level.DEBUG;
            case 3:
                return Level.TRACE;
            default:
                return m_verbosity.length > 3 ? Level.ALL : Level.WARN;
        }
    }

    private static class InetSocketAddressConverter implements ITypeConverter<InetSocketAddress> {
        public InetSocketAddress convert(String value) throws Exception {
            HostAndPort hp = HostAndPort.fromString(value).requireBracketsForIPv6();
            HostSpecifier.from(hp.getHost());
            return new InetSocketAddress(hp.getHost(), hp.getPortOrDefault(DEFAULT_PORT));
        }
    }
}
