package de.melb00m.tr4o.tiles;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.exceptions.Exceptions;
import de.melb00m.tr4o.helper.FileHelper;
import de.melb00m.tr4o.helper.OutputHelper;
import de.melb00m.tr4o.misc.Verify;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class that scans the user's X-Plane sceneries for tiles that represent ortho-scenery and
 * ortho-overlays.
 *
 * <p>This class more or less only tries to detect these types of scenery using a handful of
 * detection-methods. The interpretation of the results, such as cross-referencing the detected
 * overlay- and scenery-tiles, is done in the {@link TilesScannerResult}-class.
 *
 * @see TilesScannerResult
 */
public class TilesScanner {

  private static final Logger LOG = LogManager.getLogger(TilesScanner.class);
  private static final String EARTH_NAV_DATA =
      Transparency4Ortho.CONFIG.getString("overlay-scanner.earth-nav-data-folder");
  private static final Pattern DSF_TILE_FILENAME_PATTERN =
      Pattern.compile(
          Transparency4Ortho.CONFIG.getString("overlay-scanner.regex.dsf-filename"),
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SCENERY_PACK_ENTRY_PATTERN =
      Pattern.compile(
          Transparency4Ortho.CONFIG.getString("overlay-scanner.regex.scenery-pack-entry"));
  private static final Set<Pattern> ORTHO_FOLDER_NAME_PATTERNS =
      Transparency4Ortho.CONFIG.getStringList("overlay-scanner.detection.orthos.folder-names-regex")
          .stream()
          .map(Pattern::compile)
          .collect(Collectors.toUnmodifiableSet());
  private static final Pattern ORTHO_TEXTURE_FILENAME_PATTERN =
      Pattern.compile(
          Transparency4Ortho.CONFIG.getString(
              "overlay-scanner.detection.orthos.dds-filename-regex"),
          Pattern.CASE_INSENSITIVE);
  private static final Set<Path> ORTHO_DETECTION_EXCLUDERS =
      Transparency4Ortho.CONFIG.getStringList("overlay-scanner.detection.orthos.excluder-files")
          .stream()
          .map(Path::of)
          .collect(Collectors.toUnmodifiableSet());
  private static final Set<Path> ORTHO_DETECTION_INCLUDERS =
      Transparency4Ortho.CONFIG.getStringList("overlay-scanner.detection.orthos.includer-files")
          .stream()
          .map(Path::of)
          .collect(Collectors.toUnmodifiableSet());

  private final Transparency4Ortho command;
  private final Path xPlaneRootDir;
  private final Set<Path> sceneryDirectories;

  public TilesScanner(final Transparency4Ortho command) {
    this.command = command;
    this.xPlaneRootDir = command.getXPlanePath();
    this.sceneryDirectories = calcXplaneSceneryFolders();
  }

  private Set<Path> calcXplaneSceneryFolders() {
    final var sceneryPacksFile =
        xPlaneRootDir.resolve(command.config().getString("overlay-scanner.scenery-packs-file"));
    try {
      return Files.readAllLines(sceneryPacksFile).stream()
          .map(SCENERY_PACK_ENTRY_PATTERN::matcher)
          .filter(Matcher::matches)
          .map(match -> Paths.get(match.group("scenerypath")))
          .map(path -> path.isAbsolute() ? path : xPlaneRootDir.resolve(path))
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format("Failed to read sceneries from %s", sceneryPacksFile));
    }
  }

  /**
   * Scans the X-Plane-folder for ortho-tiles and {@link TilesScannerResult}-object representing the
   * results in a structured form.
   *
   * @return Result of the scan
   */
  public TilesScannerResult scanForOrthoScenery() {
    final var orthoFolderToDsfMap = new HashSetValuedHashMap<Path, Path>();
    final var orthoFolders =
        findOrthoDirectories(command.getOrthoSceneryPaths().orElse(sceneryDirectories));

    orthoFolders.forEach(
        folder ->
            Verify.withErrorMessage(
                    "Ortho-scenery does not contain required '%s'-folder: %s",
                    EARTH_NAV_DATA, folder)
                .argument(Files.isDirectory(folder.resolve(EARTH_NAV_DATA))));

    orthoFolders.forEach(
        dir ->
            getDsfFilesFromPath(dir.resolve(EARTH_NAV_DATA))
                .forEach(dsf -> orthoFolderToDsfMap.put(dir, dsf)));

    return new TilesScannerResult(orthoFolderToDsfMap);
  }

  private Set<Path> findOrthoDirectories(final Collection<Path> in) {
    LOG.info("Scanning your X-Plane installation for ortho-sceneries (this may take a moment)");
    return OutputHelper.maybeShowWithProgressBar(
            "Scanning for Orthos", in.stream(), Level.TRACE, command)
        .flatMap(FileHelper::walk)
        .filter(this::isPotentialOrthoTilesDirectory)
        .collect(Collectors.toSet());
  }

  private Set<Path> getDsfFilesFromPath(final Path source) {
    try (final var stream = Files.walk(source)) {
      return stream
          .filter(
              path -> DSF_TILE_FILENAME_PATTERN.matcher(path.getFileName().toString()).matches())
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw Exceptions.unrecoverable(e);
    }
  }

  private boolean isPotentialOrthoTilesDirectory(final Path dir) {
    // must be part of scenery directories
    if (!sceneryDirectories.contains(dir.toAbsolutePath())) {
      LOG.trace(
          "{} is NOT an (active) ortho folder, as it is not contained in the scenery_pack.ini",
          dir);
      return false;
    }
    // basic check: needs to be a directory and contain Earth nav data
    if (!Files.isDirectory(dir.resolve(EARTH_NAV_DATA))) {
      LOG.trace(
          "{} is NOT an ortho-folder, as it does not contain an {} folder", dir, EARTH_NAV_DATA);
      return false;
    }
    // may not contain any file that signals it is _not_ an ortho-scenery dir
    var excluder = firstMatchInFolder(dir, ORTHO_DETECTION_EXCLUDERS);
    if (excluder.isPresent()) {
      LOG.trace("{} is NOT an ortho-folder as it contains excluder-file {}", dir, excluder.get());
      return false;
    }
    // well-known folder names that match ortho-scenery
    if (ORTHO_FOLDER_NAME_PATTERNS.stream()
        .anyMatch(pattern -> pattern.matcher(dir.getFileName().toString()).matches())) {
      LOG.trace("{} is likely an ortho-folder due to it's directory-name", dir);
      return true;
    }
    // contained files that indicate ortho-scenery
    var includer = firstMatchInFolder(dir, ORTHO_DETECTION_INCLUDERS);
    if (includer.isPresent()) {
      LOG.trace(
          "{} is likely an ortho-folder as it contains includer-file {}", dir, includer.get());
      return true;
    }
    // This last check is a little more expensive, as we check for DDS tile-textures with matching
    // ortho-style naming
    var texturesDir = dir.resolve("textures");
    if (Files.isDirectory(texturesDir) && folderContainsOnlyOrthoDdsTextures(texturesDir)) {
      LOG.trace(
          "{} is likely an ortho-folder as it contains only ortho-style-named DDS-textures", dir);
      return true;
    }
    LOG.trace("{} is NOT an ortho-folder, as nothing was found that would indicate it was", dir);
    return false;
  }

  private Optional<Path> firstMatchInFolder(
      final Path rootFolder, final Collection<Path> localLookup) {
    var match = localLookup.stream().map(rootFolder::resolve).filter(Files::exists).findFirst();
    return match.map(rootFolder::relativize);
  }

  private boolean folderContainsOnlyOrthoDdsTextures(final Path texturesFolder) {
    try (final var textureStream = Files.walk(texturesFolder)) {
      final Set<Path> ddsTextures =
          textureStream
              .filter(file -> file.getFileName().toString().toLowerCase().endsWith(".dds"))
              .collect(Collectors.toSet());
      return !ddsTextures.isEmpty()
          && ddsTextures.stream()
              .allMatch(
                  dds ->
                      ORTHO_TEXTURE_FILENAME_PATTERN
                          .matcher(dds.getFileName().toString())
                          .matches());
    } catch (IOException e) {
      throw Exceptions.unrecoverable(e);
    }
  }
}
