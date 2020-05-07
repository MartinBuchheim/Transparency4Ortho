package de.melb00m.tr4o.app.subcommands;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.library.LibraryGenerator;
import de.melb00m.tr4o.tiles.TilesScanner;
import de.melb00m.tr4o.tiles.TilesScannerResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 * "All-in-One" transparency generation class.
 *
 * <p>This is the main operating mode of the application that combines all necessary step to get
 * Transparency4Ortho up and running in X-Plane.
 *
 * @see Transparency4Ortho
 * @author Martin Buchheim
 */
public class TransparentRoadsGenerator implements Runnable {

  private static final String GITHUB_URL =
      Transparency4Ortho.CONFIG.getString("general.github-url");
  private static final Logger LOG = LogManager.getLogger(TransparentRoadsGenerator.class);
  private final LibraryGenerator libraryGenerator;
  private final TilesScanner tilesScanner;
  private final Transparency4Ortho command;
  private int currentStep = 0;

  public TransparentRoadsGenerator(final Transparency4Ortho command) {
    this.command = command;
    this.libraryGenerator = new LibraryGenerator(command);
    this.tilesScanner = new TilesScanner(command);
  }

  @Override
  public void run() {
    nextStep("Preparing Transparency4Ortho Library");
    final var newLibraryCreated = libraryGenerator.validateOrCreateLibrary();

    nextStep("Scanning for Ortho-Scenery");
    final var scannerResult = tilesScanner.scanForOrthoScenery();
    if (scannerResult.getOrthoCoveredTiles().isEmpty()) {
      LOG.info("No ortho-scenery covered tiles were detected.");
      return;
    }

    nextStep("Generating Library-File");
    generateLibraryDefinition(scannerResult);

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

  private void generateLibraryDefinition(final TilesScannerResult scannerResult) {
    LOG.info(
        "The following ortho-sceneries were detected and will be used in the Transparency4Ortho library: ");
    scannerResult.getOrthoFolderToDsfMap().keySet().stream()
        .map(folder -> String.format("  > %s", folder.toAbsolutePath()))
        .sorted()
        .forEach(LOG::info);
    libraryGenerator.generateLibraryTxt(
        scannerResult.getOrthoCoveredTiles(), command.isRemoveExistingEntries());
  }

  private void printFinalWords(boolean newLibraryCreated) {
    LOG.info("Transparency4Ortho processing has finished.");
    LOG.info(
        "The detected ortho-sceneries are now covered by the modded roads from the Transparency4Ortho library.");
    if (newLibraryCreated) {
      if (command.isSkipLibraryModifications()) {
        LOG.info(
            "As you have skipped the automatic transparency-modifications, you can set up the contents in this folder to your liking to achieve transparency.");
      } else {
        LOG.info(
            "The library has been set up with the automatic transparency-modifications applied. However, you can still safely apply additional changes, if you're not fully happy with the result.");
      }
    }
    LOG.info("If additional ortho-scenery should be added later on, simply re-run this command.");
    LOG.info("For help or newer versions, check out {}.", GITHUB_URL);
    LOG.info("Always Three Greens!");
  }
}
