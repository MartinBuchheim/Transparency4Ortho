package de.melb00m.tr4o.library;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.exceptions.Exceptions;
import de.melb00m.tr4o.helper.FileHelper;
import de.melb00m.tr4o.misc.Verify;
import de.melb00m.tr4o.tiles.Tile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class that allows to (re-) create and verify the Transparency4Ortho library in X-Plane.
 *
 * <p>The Transparency4Ortho library is a copy of the default X-Plane roads-library, including the
 * {@code roads.net} and {@code roads_EU.net} files which will be duplicate in a separate
 * Transparency4Ortho-folder under {@code X-Plane/Custom Scenery}.
 *
 * <p>The library will provide the roads-files mentioned above under a new name, which will then be
 * linked in the overlay-tiles of ortho-scenery to keep the original way that roads look intact for
 * regular (non-ortho) ground textures that X-Plane generates on the fly.
 *
 * <p>With {@link #applyLibraryModifications()} the {@code roads(_EU).net}-files are also
 * automatically changed to achieve the transparency effect.
 *
 * @author Martin Buchheim
 */
public class LibraryGenerator {

  private static final Logger LOG = LogManager.getLogger(LibraryGenerator.class);
  private static final String EXPORT_DIRECTIVE =
      Transparency4Ortho.CONFIG.getString("libgen.generation.export-directive");
  private static final List<String> LIB_TXT_HEADERS =
      Transparency4Ortho.CONFIG.getStringList("libgen.generation.library-header");
  private static final String REGION_RECT_FORMAT =
      Transparency4Ortho.CONFIG.getString("libgen.generation.region-rect-format");
  private static final String REGION_DEFINE_FORMAT =
      Transparency4Ortho.CONFIG.getString("libgen.generation.region-define-format");
  private static final String REGION_USE_FORMAT =
      Transparency4Ortho.CONFIG.getString("libgen.generation.region-use-format");

  private final Transparency4Ortho command;
  private final String libraryPrefix;
  private final Path libraryFolder;
  private final Path libraryDefinitionFile;
  private final Path roadLibraryTargetFolder;
  private final Path roadsLibrarySourceFolder;
  private final Set<Path> roadsLibraryExcludes;
  private final Set<String> roadsLibraryExportDefinitions;
  private final Set<Path> modifyUncommentRoadFiles;

  public LibraryGenerator(final Transparency4Ortho command) {
    this.command = command;
    final var xplanePath = command.getXPlanePath();
    this.libraryPrefix = command.config().getString("libgen.generation.library-prefix");
    this.libraryFolder = xplanePath.resolve(command.config().getString("libgen.library.folder"));
    this.libraryDefinitionFile =
        xplanePath.resolve(command.config().getString("libgen.library.definition-file"));
    this.roadLibraryTargetFolder =
        xplanePath.resolve(command.config().getString("libgen.resources.roads.target"));
    this.roadsLibrarySourceFolder =
        xplanePath.resolve(command.config().getString("libgen.resources.roads.source"));
    this.roadsLibraryExcludes =
        command.config().getStringList("libgen.resources.roads.duplication.ignore-files").stream()
            .map(xplanePath::resolve)
            .collect(Collectors.toSet());
    this.roadsLibraryExportDefinitions =
        Set.copyOf(command.config().getStringList("libgen.resources.roads.exports"));
    this.modifyUncommentRoadFiles =
        command.config().getStringList("libgen.modifications.roads.uncomment.target-files").stream()
            .map(xplanePath::resolve)
            .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Checks if a Transparency4Ortho library is present in the X-Plane folder, and if so, runs some
   * basic validations that is correct.
   *
   * <p>If no library is found, a new one is created.
   *
   * @return {@code true} if the library has been created
   */
  public boolean validateOrCreateLibrary() {
    synchronized (LibraryGenerator.class) {
      try {
        if (Files.exists(libraryFolder)) {
          LOG.info("Transparency4Ortho library is already present: {}", libraryFolder);
          return false;
        }
        createLibrary();
        return true;
      } catch (IOException e) {
        throw new IllegalStateException(
            String.format("There was a problem initializing the library at: %s", libraryFolder), e);
      }
    }
  }

  private void createLibrary() throws IOException {
    Verify.withErrorMessage(
            "Can't find X-Plane default roads-library at expected location: %s",
            roadsLibrarySourceFolder)
        .state(Files.exists(roadsLibrarySourceFolder));
    validateRoadsLibraryChecksum();
    copyLibraryFolder();
    applyLibraryModifications();
  }

  private void validateRoadsLibraryChecksum() {
    var crcConfig = command.config().getConfig("libgen.resources.roads.checksum");
    crcConfig
        .entrySet()
        .forEach(
            entry -> {
              var file = roadsLibrarySourceFolder.resolve(entry.getKey().replace("\"", ""));
              var fileCrc = FileHelper.deepMD5Hash(file).toUpperCase();
              var expectedCrcs = crcConfig.getStringList(entry.getKey());
              if (!expectedCrcs.contains(fileCrc)) {
                LOG.info("Checksum mismatch for file at: {}", file);
                LOG.debug("Checksum was {} (Expected: {})", fileCrc, expectedCrcs);
                LOG.info(
                    "If you have made changes to your X-Plane default roads-library, please run the X-Plane installer again to reset it.");
                LOG.info(
                    "If this error persists afterwards, you might use an unsupported version of X-Plane.");
                Verify.withErrorMessage(
                        "Aborting. Use '-i' if you want to ignore the checksum mismatch.")
                    .state(command.isIgnoreChecksumErrors());
              }
            });
  }

  private void copyLibraryFolder() throws IOException {
    LOG.info("Copying X-Plane default roads-library to {}", libraryFolder);
    Files.createDirectories(roadLibraryTargetFolder);
    FileHelper.copyRecursively(
        roadsLibrarySourceFolder,
        roadLibraryTargetFolder,
        roadsLibraryExcludes.toArray(new Path[0]));
  }

  private void applyLibraryModifications() {
    if (command.isSkipLibraryModifications()) {
      LOG.info("Skipping automatic library modifications");
      return;
    }
    LOG.info("Applying modifications for transparent roads");
    final var uncommentStartingWith =
        command.config().getStringList("libgen.modifications.roads.uncomment.lines-starting-with")
            .stream()
            .collect(Collectors.toUnmodifiableSet());
    final var groupPattern =
        Pattern.compile(
            command.config().getString("libgen.modifications.roads.uncomment.groups-regex"));
    final var uncommentEnabledGroups =
        command.config().getStringList("libgen.modifications.roads.uncomment.groups-enabled")
            .stream()
            .collect(Collectors.toUnmodifiableSet());
    try {
      for (final var fileToModify : modifyUncommentRoadFiles) {
        Verify.withErrorMessage("File not writeable for modification: %s", fileToModify)
            .state(Files.isWritable(fileToModify));
        final var lines = Files.readAllLines(fileToModify);
        final var newLines = new ArrayList<String>(lines.size());
        final var groups = new ArrayList<String>();
        var uncommentEnabledBlock = false;
        for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
          final var line = lines.get(lineNo);
          var matcher = groupPattern.matcher(line);
          if (matcher.matches()) {
            final var groupName = matcher.group("groupName");
            groups.add(groupName);
            uncommentEnabledBlock = uncommentEnabledGroups.contains(groupName);
          }
          final var uncomment =
              uncommentEnabledBlock && uncommentStartingWith.stream().anyMatch(line::startsWith);
          final var newLine = uncomment ? String.format("#(Transparency4Ortho) %s", line) : line;
          if (uncomment) {
            LOG.trace(
                "Line {} in {} changed from '{}' to '{}'", lineNo + 1, fileToModify, line, newLine);
          }
          newLines.add(newLine);
        }
        LOG.trace("Groups identified in file {}: {}", fileToModify, groups);
        if (!Objects.equals(lines, newLines)) {
          Files.write(fileToModify, newLines);
          LOG.debug("Modifications saved in {}", fileToModify);
        }
      }
    } catch (IOException e) {
      throw Exceptions.unrecoverable(e);
    }
  }

  /**
   * Generates the library.txt file containing with the given tiles mapped to our road-network
   * definitions.
   *
   * @param tiles Tiles for which to use the modded road networks
   * @param removeExistingEntries Keep existing definitions intact
   * @throws IOException I/O Exception
   */
  public void generateLibraryTxt(
      final Collection<Tile> tiles, final boolean removeExistingEntries) {
    LOG.info("Generating library at {}", libraryDefinitionFile);
    final var regionName = command.config().getString("libgen.generation.region-name");
    try {
      // set header
      final var libraryLines = new ArrayList<>(LIB_TXT_HEADERS);

      // add region definition
      libraryLines.add(String.format(REGION_DEFINE_FORMAT, regionName));

      // add region-rects
      final var regionRects = new TreeSet<String>();
      if (!removeExistingEntries) {
        regionRects.addAll(fetchExistingRegionRects(libraryDefinitionFile));
        LOG.debug("{} tile-definitions from previous runs will be kept", regionRects.size());
      }
      tiles.stream().map(this::formatTileToRegionRect).forEach(regionRects::add);
      libraryLines.addAll(regionRects);

      // use region and append exports
      libraryLines.add("");
      libraryLines.add(String.format(REGION_USE_FORMAT, regionName));
      roadsLibraryExportDefinitions.stream()
          .map(export -> buildExportDirective(export, roadLibraryTargetFolder.resolve(export)))
          .forEach(libraryLines::add);

      Files.write(libraryDefinitionFile, libraryLines);
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format("Failed to generate library.txt at: %s", libraryDefinitionFile), e);
    }
  }

  private Set<String> fetchExistingRegionRects(final Path libraryTxt) throws IOException {
    if (!Files.exists(libraryTxt)) {
      return Collections.emptySet();
    }
    final var pattern =
        Pattern.compile(command.config().getString("libgen.generation.region-rect-regex"));
    return Files.readAllLines(libraryTxt).stream()
        .filter(line -> pattern.matcher(line).matches())
        .collect(Collectors.toSet());
  }

  private String formatTileToRegionRect(final Tile tile) {
    return String.format(REGION_RECT_FORMAT, tile.getLongitude(), tile.getLatitude());
  }

  private String buildExportDirective(final String exportName, final Path fileLocation) {
    Verify.withErrorMessage("File to be used for export directive does not exist: %s", fileLocation)
        .state(Files.exists(fileLocation));
    final var relativePath = libraryFolder.relativize(fileLocation).toString().replace('\\', '/');
    return String.format(EXPORT_DIRECTIVE, libraryPrefix, exportName, relativePath);
  }

  /**
   * Removes an existing Transparency4Ortho-library (if it exists) and creates it new from scratch.
   */
  public void regenerateLibrary() {
    synchronized (LibraryGenerator.class) {
      LOG.info("Regenerating library at: {}", libraryFolder);
      try {
        if (Files.exists(libraryFolder)) {
          LOG.debug("Deleting existing library at: {}", libraryFolder);
          FileHelper.deleteRecursively(libraryFolder);
        }
        createLibrary();
      } catch (IOException e) {
        throw new IllegalStateException(
            String.format("Failed to regenerate library at: %s", libraryFolder), e);
      }
    }
  }

  /**
   * Returns the library folder for Transparency4Ortho inside X-Plane
   *
   * @return Path to Transparency4Ortho library
   */
  public Path getLibraryFolder() {
    return libraryFolder;
  }
}
