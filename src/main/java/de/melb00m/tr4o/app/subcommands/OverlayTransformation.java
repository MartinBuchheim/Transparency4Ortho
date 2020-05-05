package de.melb00m.tr4o.app.subcommands;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.helper.OutputHelper;
import de.melb00m.tr4o.library.LibraryGenerator;
import de.melb00m.tr4o.overlay.OverlayScanner;
import de.melb00m.tr4o.overlay.OverlayScannerResult;
import de.melb00m.tr4o.overlay.OverlayTileTransformer;
import de.melb00m.tr4o.xptools.XPToolsInterface;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class OverlayTransformation implements Runnable {

  private static final String GITHUB_URL =
      Transparency4Ortho.CONFIG.getString("general.github-url");
  private static final Logger LOG = LogManager.getLogger(OverlayTransformation.class);
  private final LibraryGenerator libraryGenerator;
  private final OverlayScanner overlayScanner;
  private final Transparency4Ortho command;
  private int currentStep = 0;

  public OverlayTransformation(final Transparency4Ortho command) {
    this.command = command;
    this.libraryGenerator = new LibraryGenerator(command);
    this.overlayScanner = new OverlayScanner(command);
  }

  @Override
  public void run() {
    nextStep("Preparing Transparency4Ortho Library");
    final var newLibraryCreated = libraryGenerator.validateOrCreateLibrary();

    nextStep("Scanning Overlay- and Ortho-Tiles");
    final var scannerResult = overlayScanner.scanOverlaysAndOrthos();
    if (scannerResult.getIntersectingTiles().isEmpty()) {
      LOG.info("No tiles found that need to be processed.");
      return;
    }
    LOG.debug("Following tiles have both, overlays and orthos, present:");
    OutputHelper.joinLinesByTotalLength(
            90, ", ", new TreeSet<>(scannerResult.getIntersectingTiles()))
        .forEach(out -> LOG.debug("    {}", out));

    nextStep("Transforming Overlays");
    userConfirmDetectedOrthos(scannerResult);
    startOverlayTransformation(scannerResult.getIntersectingOverlayDsfs());

    nextStep("Final Words");
    printFinalWords(newLibraryCreated);
  }

  private void nextStep(final String name) {
    currentStep += 1;
    LOG.info(
        "=====================================================================================================");
    LOG.info("    STEP {}:   {}", currentStep, name);
    LOG.info(
        "=====================================================================================================");
  }

  private void userConfirmDetectedOrthos(final OverlayScannerResult scannerResult) {
    LOG.info("The following overlay-sceneries are covered (at least in part) by ortho-imagery:");
    scannerResult
        .getIntersectingOverlayToSceneryDirectoriesMap()
        .asMap()
        .entrySet()
        .forEach(
            entry -> {
              LOG.info("    ::: OVERLAYS IN : '{}' COVER ", entry.getKey().getFileName());
              final var sortedNames =
                  entry.getValue().stream()
                      .map(path -> path.getFileName().toString())
                      .sorted()
                      .collect(Collectors.toList());
              OutputHelper.joinLinesByTotalLength(90, ", ", sortedNames)
                  .forEach(line -> LOG.info("      |  {}", line));
            });
    LOG.info(
        "These tiles will be modified to use the Transparency4Ortho library to make smaller roads transparent.");
    LOG.info("If the above looks reasonable to you, proceed with the transformation.");
    LOG.info(
        "No worries: every overlay that needs to be transformed will be backed up beforehand.");
    OutputHelper.confirmYesOrExit(
        false,
        () -> "Proceed with transformation?",
        () ->
            String.format(
                "Please check the documentation at %s for more information about controlling the ortho-scenery auto-detection.",
                GITHUB_URL));
  }

  private Set<Path> startOverlayTransformation(final Set<Path> overlaysToTransform) {
    final var backupFolder =
        command
            .getBackupPath()
            .orElseGet(
                () ->
                    command
                        .getXPlanePath()
                        .resolve(command.config().getString("overlay-scanner.backup-folder")))
            .resolve(new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()));
    final var libraryName = command.config().getString("overlay-scanner.library-prefix");

    final var xpTools = new XPToolsInterface(command);
    final var transformers =
        overlaysToTransform.stream()
            .map(tile -> new OverlayTileTransformer(tile, backupFolder, libraryName, xpTools))
            .collect(Collectors.toUnmodifiableSet());

    OutputHelper.maybeShowWithProgressBar(
            "Transforming Overlays", transformers.parallelStream(), Level.DEBUG, command)
        .forEach(OverlayTileTransformer::run);

    // check which tiles have been transformed
    final Set<Path> transformedTiles =
        transformers.stream()
            .filter(OverlayTileTransformer::isTransformed)
            .map(OverlayTileTransformer::getDsfFile)
            .collect(Collectors.toUnmodifiableSet());
    LOG.trace("Tiles that have been transformed: {}", transformedTiles.toArray());
    LOG.info(
        "{} Overlay tiles have been linked to Transparency4Ortho, {} did not need changes.",
        transformedTiles.size(),
        transformers.size() - transformedTiles.size());
    if (!transformedTiles.isEmpty()) {
      LOG.info("Backups have been stored in {}", backupFolder);
    }

    return transformedTiles;
  }

  private void printFinalWords(boolean newLibraryCreated) {
    LOG.info("Overlay processing has finished.");
    LOG.info(
        "The transformed overlays now use the roads-library found in {}.",
        libraryGenerator.getLibraryFolder());
    if (newLibraryCreated) {
      if (command.isSkipLibraryModifications()) {
        LOG.info(
            "As you have skipped the automatic transparency-modifications, you can set up the contents in this folder to your liking to achieve transparency.");
      } else {
        LOG.info(
            "The library has been set up with the automatic transparency-modifications applied. However, you can still safely apply additional changes, if you're not fully happy with the result.");
      }
    } else {
      LOG.info("If you wish to re-generate this library, have a look at the '-r' parameter.");
    }
    LOG.info("If additional overlays should be added later on, simply re-run this command.");
    LOG.info("For help or newer versions, check out {}.", GITHUB_URL);
    LOG.info("Always Three Greens!");
  }
}
