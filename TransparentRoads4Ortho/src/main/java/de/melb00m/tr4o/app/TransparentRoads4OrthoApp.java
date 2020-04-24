package de.melb00m.tr4o.app;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

public class TransparentRoads4OrthoApp {

  private static final Logger LOG = LogManager.getLogger(TransparentRoads4OrthoApp.class);
  private static final String DEFAULT_LIBRARY_FOLDER = "aaa_TransparentRoads4Ortho";
  private static final String DEFAULT_LIBRARY_PREFIX = "transparentRoads4Ortho";
  private static final ImmutableSet<String> REQUIRED_OPTIONS = ImmutableSet.of("xp", "t", "o");

  public static void main(String[] args) {
    final Options options = generateCliOptions();
    try {
      final CommandLine line = new DefaultParser().parse(options, args);

      if (line.hasOption("h")) {
        printHelp(options);
      } else {
        verifyArguments(line);
      }

    } catch (IllegalArgumentException | ParseException ex) {
      LOG.always().log(ex.getMessage());
      LOG.always().log("Use --help to show usage information");
    }
  }

  private static void verifyArguments(CommandLine line) {
    final String missingArgs =
        REQUIRED_OPTIONS.stream()
            .filter(opt -> !line.hasOption(opt))
            .map(opt -> "-" + opt)
            .collect(Collectors.joining(", "));
    Preconditions.checkArgument(
        missingArgs.isEmpty(), "Missing required arguments: %s", missingArgs);
  }

  private static void printHelp(Options options) {
    final HelpFormatter help = new HelpFormatter();
    help.printHelp(
        120,
        "TransparentRoads4OrthoApp -xp <path-to-xplane> -t <path-to-tiles> -o <path-to-tile-overlays>",
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
                DEFAULT_LIBRARY_FOLDER))
        .addOption(
            "lp",
            "LibraryPrefix",
            true,
            String.format(
                "Prefix used in Overlay-DSF for the TransparentRoads4Ortho-library. Defaults to '%s'",
                DEFAULT_LIBRARY_PREFIX))
        .addOption("v", "verbose", false, "Show verbose log output")
        .addOption("version", "Display version")
        .addOption("h", "help", false, "Show detailed usage information");
  }
}
