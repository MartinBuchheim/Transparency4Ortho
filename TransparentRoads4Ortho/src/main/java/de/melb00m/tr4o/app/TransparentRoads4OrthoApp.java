package de.melb00m.tr4o.app;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import de.melb00m.tr4o.proc.RunProcessor;
import lombok.val;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Collectors;

public class TransparentRoads4OrthoApp {

  private static final Logger LOG = LogManager.getLogger(TransparentRoads4OrthoApp.class);
  private static final ImmutableSet<String> REQUIRED_OPTIONS = ImmutableSet.of("xp", "t", "o");

  public static void main(String[] args) {
    val options = generateCliOptions();
    if (args.length == 0) {
      printHelp(options);
      return;
    }
    try {
      val line = new DefaultParser().parse(options, args);
      if (line.hasOption("h")) {
        printHelp(options);
        return;
      }
      if (line.hasOption("version")) {
        throw new UnsupportedOperationException("Versioning currently not enabled");
      }
      if (line.hasOption("v")) {
        Configurator.setRootLevel(Level.DEBUG);
      }

      val config = readAndVerifyArguments(line);
      new RunProcessor(config).startProcessing();

    } catch (IllegalArgumentException | ParseException ex) {
      LOG.warn(ex.getMessage());
      LOG.info("Use --help to show usage information");
    }
  }

  private static TransparentRoads4OrthoConfig readAndVerifyArguments(CommandLine line) {
    val missingArgs =
        REQUIRED_OPTIONS.stream()
            .filter(opt -> !line.hasOption(opt))
            .map(opt -> "-" + opt)
            .collect(Collectors.joining(", "));
    Preconditions.checkArgument(
        missingArgs.isEmpty(), "Missing required arguments: %s", missingArgs);

    val xPlanePath = Paths.get(line.getOptionValue("xp"));
    val tilesPath = Paths.get(line.getOptionValue("t"));
    val overlayPath = Paths.get(line.getOptionValue("o"));
    val dsfToolExec = getOptionalPath(line, "dx");

    Preconditions.checkArgument(
        Files.isReadable(xPlanePath), "X-Plane location is not readable: %s", xPlanePath);
    Preconditions.checkArgument(
        Files.isReadable(tilesPath), "Ortho-Tile location is not readable: %s", tilesPath);
    Preconditions.checkArgument(
        Files.isReadable(overlayPath), "Ortho-Overlays location is not readable: %s", overlayPath);
    dsfToolExec.ifPresent(
        dx ->
            Preconditions.checkArgument(
                Files.isExecutable(dx), "DSFTool is not executable: %s", dx));

    return new TransparentRoads4OrthoConfig(
        xPlanePath,
        tilesPath,
        overlayPath,
        getOptionalPath(line, "dx"),
        getOptionalPath(line, "b"),
        Optional.ofNullable(line.getOptionValue("lf")),
        Optional.ofNullable(line.getOptionValue("lp")));
  }

  private static Optional<Path> getOptionalPath(CommandLine line, String config) {
    if (line.hasOption(config)) {
      return Optional.of(Paths.get(line.getOptionValue(config)));
    }
    return Optional.empty();
  }

  private static void printHelp(Options options) {
    val help = new HelpFormatter();
    help.printHelp(
        120,
        "TransparentRoads4Ortho -xp <path-to-xplane> -t <path-to-tiles> -o <path-to-tile-overlays>",
        "",
        options,
        "");
  }

  private static Options generateCliOptions() {
    return new Options()
        .addOption("xp", "XPlanePath", true, "Path to main X-Plane 11 folder")
        .addOption(
            "t",
            "OrthoTilesPath",
            true,
            "Path from which the Ortho-Tiles should be retrieved. This path (and it's subfolders) will be scanned for DSF-files to identify which tiles exist. Nothing will be modified in this folder.")
        .addOption(
            "o",
            "OverlayPath",
            true,
            "Path where the overlays for the Ortho-Tiles are stored. This path (and it's subfolders) will be scanned for DSF-files that are matching those from the OrthoTilesPath. DSF-files containing references to the default road-network will be backed up & replaced with a reference to the transparent roads network.")
        .addOption(
            "dx",
            "DSFToolExecutable",
            true,
            "Path to DSFTool Executable. If not given, an attempt is made to automatically download and store the tool from the X-Plane Developer site.")
        .addOption(
            "b",
            "OverlayBackupPath",
            true,
            "Folder in which the Overlay-Backups will be stored before they are modified. Defaults to '<X-Plane-Folder>/TransparentRoads4Ortho/backups/<current-date>' in the application folder.")
        .addOption(
            "lf",
            "LibraryFolderName",
            true,
            String.format(
                "Name of the library folder that will be created under '<X-Plane>/Custom Scenery'. Defaults to '%s'.",
                TransparentRoads4OrthoConfig.DEFAULT_LIBRARY_FOLDER))
        .addOption(
            "lp",
            "LibraryPrefix",
            true,
            String.format(
                "Prefix used in Overlay-DSF for the TransparentRoads4Ortho-library. Defaults to '%s'",
                TransparentRoads4OrthoConfig.DEFAULT_LIBRARY_PREFIX))
        .addOption("v", "verbose", false, "Show verbose log output")
        .addOption("version", "Display version")
        .addOption("h", "help", false, "Show detailed usage information");
  }
}
