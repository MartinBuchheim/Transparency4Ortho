package de.melb00m.tr4o.app;

import de.melb00m.tr4o.helper.FileHelper;
import de.melb00m.tr4o.helper.OutputHelper;
import de.melb00m.tr4o.proc.LibraryGenerator;
import de.melb00m.tr4o.proc.OverlayScanner;
import de.melb00m.tr4o.proc.OverlayTileTransformer;
import de.melb00m.tr4o.proc.XPToolsInterface;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

public class AppController {

  private static final Logger LOG = LogManager.getLogger(AppController.class);
  private final LibraryGenerator libraryGenerator = new LibraryGenerator();
  private final OverlayScanner overlayScanner = new OverlayScanner();
  private int currentStep = 0;

  public void startProcessing() {
    synchronized (AppController.class) {
      newStep("Preparing Transparency4Ortho Library");
      libraryGenerator.validateOrCreateLibrary();

      newStep("Scanning Overlay- and Ortho-Tiles");
      final var orthoToOverlayMap = overlayScanner.scanForOverlaysToTransform();
      if (orthoToOverlayMap.isEmpty()) {
        LOG.info("No tiles found that need to be processed.");
        return;
      }

      newStep("Transforming Overlays");
      userConfirmDetectedOrthos(orthoToOverlayMap);
      startOverlayTransformation(
          orthoToOverlayMap.values().stream().collect(Collectors.toUnmodifiableSet()));
    }
  }

  private void newStep(final String name) {
    currentStep += 1;
    LOG.info(
        "=====================================================================================================");
    LOG.info("    STEP {}:   {}", currentStep, name);
    LOG.info(
        "=====================================================================================================");
  }

  private Set<Path> startOverlayTransformation(final Set<Path> overlaysToTransform) {

    final var backupFolder =
        AppConfig.getRunArguments()
            .getBackupPath()
            .orElseGet(
                () ->
                    AppConfig.getRunArguments()
                        .getXPlanePath()
                        .resolve(
                            AppConfig.getApplicationConfig()
                                .getString("overlay-scanner.backup-folder")))
            .resolve(new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()));
    final var libraryName =
        AppConfig.getApplicationConfig().getString("overlay-scanner.library-prefix");

    final var xpTools = new XPToolsInterface();
    final var transformers =
        overlaysToTransform.stream()
            .map(tile -> new OverlayTileTransformer(tile, backupFolder, libraryName, xpTools))
            .collect(Collectors.toUnmodifiableSet());

    ProgressBar.wrap(
            transformers.parallelStream(),
            OutputHelper.getAutoPreconfiguredBuilder(Level.TRACE, LOG).setTaskName("Transforming"))
        .forEach(OverlayTileTransformer::runTransformation);

    // check which tiles have been transformed
    final Set<Path> transformedTiles =
        transformers.stream()
            .filter(OverlayTileTransformer::isTransformed)
            .map(OverlayTileTransformer::getDsfFile)
            .collect(Collectors.toUnmodifiableSet());
    LOG.trace("Tiles that have been transformed: {}", transformedTiles.toArray());
    LOG.info(
        "{} Overlay tiles have been transformed, {} remained untouched.",
        transformedTiles.size(),
        transformers.size() - transformedTiles.size());
    if (!transformedTiles.isEmpty()) {
      LOG.info("Backups have been stored in {}", backupFolder);
    }

    return transformedTiles;
  }

  private void userConfirmDetectedOrthos(final MultiValuedMap<Path, Path> sceneryToOverlayMap) {
    LOG.info("The following ortho-sceneries cover the given overlays with ortho-imagery:");
    sceneryToOverlayMap.keySet().stream()
        .sorted()
        .forEach(
            scenery -> {
              final var tilesList =
                  sceneryToOverlayMap.get(scenery).stream()
                      .map(FileHelper::getFilenameWithoutExtension)
                      .sorted()
                      .collect(Collectors.joining(", "));
              LOG.info("    >> {}  [Tiles: {}]", scenery.getFileName(), tilesList);
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
            "Please check the documentation for more information about controlling the ortho-scenery auto-detection.");
  }
}
