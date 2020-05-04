package de.melb00m.tr4o.app;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

public final class AppConfig {

  private static AppConfig instance;
  private final Config config;
  private final RunArguments arguments;
  private final CommandLine commandLine;

  private AppConfig(final CommandLine commandLine) {
    this.config = ConfigFactory.defaultApplication().resolve();
    this.commandLine = commandLine;
    this.arguments = readAndVerifyArguments(commandLine);
    configureLoggingOutput(arguments.getConsoleLogLevel());
  }

  static void initialize(final CommandLine cmdLine) {
    synchronized (AppConfig.class) {
      if (null != instance) {
        throw new IllegalStateException("AppConfig singleton was already initialized");
      }
      instance = new AppConfig(cmdLine);
    }
  }

  public static Config getApplicationConfig() {
    return instance.config;
  }

  public static RunArguments getRunArguments() {
    return instance.arguments;
  }

  private RunArguments readAndVerifyArguments(final CommandLine line) {
    final var args = line.getArgList();
    Validate.isTrue(args.size() >= 2, "Expecting at least 2 path-parameters with application call");

    final var xPlanePath = Path.of(args.get(0));
    final var overlayPaths =
        args.subList(1, args.size()).stream()
            .map(Paths::get)
            .collect(Collectors.toUnmodifiableSet());
    final var dsfToolExec = getOptionalPath("dsf");
    final var backupPath = getOptionalPath("b");

    Validate.isTrue(
        Files.isReadable(xPlanePath), "X-Plane location '%s' is not readable", xPlanePath);
    Validate.isTrue(!overlayPaths.isEmpty(), "No Overlay-Paths have been given");
    overlayPaths.forEach(
        path ->
            Validate.isTrue(
                Files.isReadable(path.resolve("Earth nav data")),
                "Overlay-Path '%s' does not contain expected 'Earth nav data' folder",
                path));
    dsfToolExec.ifPresent(
        dx -> Validate.isTrue(Files.isExecutable(dx), "DSFTool at '%s' is not executable", dx));
    backupPath.ifPresent(
        path ->
            Validate.isTrue(
                Files.isWritable(path), "Backup-folder is not writeable: %s", backupPath));

    var logLevel = Level.INFO;
    if (line.hasOption("d")) logLevel = Level.DEBUG;
    if (line.hasOption("dd")) logLevel = Level.TRACE;

    return new RunArguments(
        xPlanePath,
        overlayPaths,
        dsfToolExec,
        logLevel,
        line.hasOption("i"),
        line.hasOption("n"),
        backupPath);
  }

  private Optional<Path> getOptionalPath(String config) {
    return commandLine.hasOption(config)
        ? Optional.of(Path.of(commandLine.getOptionValue(config)))
        : Optional.empty();
  }

  private void configureLoggingOutput(final Level level) {
    if (level != Level.INFO) {
      Configurator.setRootLevel(level);
      // for the console-appender, we need to modify the threshold filter to the new log-level
      final var context = LoggerContext.getContext(false);
      context.getRootLogger().getAppenders().values().stream()
          .filter(ConsoleAppender.class::isInstance)
          .map(ConsoleAppender.class::cast)
          .forEach(appender -> appender.removeFilter(appender.getFilter()));
      context.updateLoggers();
    }
  }
}
