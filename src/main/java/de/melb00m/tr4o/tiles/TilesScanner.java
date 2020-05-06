package de.melb00m.tr4o.tiles;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.exceptions.ExceptionHelper;
import de.melb00m.tr4o.helper.LazyAttribute;
import org.apache.commons.collections4.SetUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
  private static final Set<Pattern> OVERLAY_FOLDER_NAME_PATTERNS =
      Transparency4Ortho.CONFIG
          .getStringList("overlay-scanner.detection.overlays.folder-names-regex").stream()
          .map(Pattern::compile)
          .collect(Collectors.toUnmodifiableSet());
  private static final Set<Path> OVERLAY_DETECTION_EXCLUDERS =
      Transparency4Ortho.CONFIG.getStringList("overlay-scanner.detection.overlays.excluder-files")
          .stream()
          .map(Path::of)
          .collect(Collectors.toUnmodifiableSet());
  private static final Set<Path> OVERLAY_DETECTION_INCLUDERS =
      Transparency4Ortho.CONFIG.getStringList("overlay-scanner.detection.overlays.includer-files")
          .stream()
          .map(Path::of)
          .collect(Collectors.toUnmodifiableSet());
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
  private final LazyAttribute<Set<Path>> sceneryDirectories;

  public TilesScanner(final Transparency4Ortho command) {
    this.command = command;
    this.xPlaneRootDir = command.getXPlanePath();
    this.sceneryDirectories = new LazyAttribute<>(this::calcXplaneSceneryFolders);
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

  public TilesScannerResult scanOverlaysAndOrthos() {
    final var ovlFolderToTilesMapping = new HashMap<Path, Path>();
    final var orthoFolderToTilesMapping = new HashMap<Path, Path>();
    findOverlayDirectories()
        .forEach(
            dir ->
                getDsfFilesFromPath(dir.resolve(EARTH_NAV_DATA))
                    .forEach(ovl -> ovlFolderToTilesMapping.put(ovl, dir)));
    findOrthoDirectories()
        .forEach(
            dir ->
                getDsfFilesFromPath(dir.resolve(EARTH_NAV_DATA))
                    .forEach(ortho -> orthoFolderToTilesMapping.put(ortho, dir)));

    // Validate the scenery-folders are distinct
    final var intersect =
        SetUtils.intersection(
            Set.copyOf(ovlFolderToTilesMapping.values()),
            Set.copyOf(orthoFolderToTilesMapping.values()));

    if (!intersect.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "These directories are used as ortho AND overlay directories: %s",
              intersect.stream()
                  .map(path -> path.getFileName().toString())
                  .collect(Collectors.joining(", "))));
    }

    return new TilesScannerResult(ovlFolderToTilesMapping, orthoFolderToTilesMapping);
  }

  private Set<Path> findOverlayDirectories() {
    final var autoScan = command.getOverlayPaths().isEmpty();
    final var scanBaseFolders = command.getOverlayPaths().orElseGet(sceneryDirectories::get);
    final var overlayFolders = new HashSet<Path>();

    if (autoScan) {
      // if using auto-scan, we check all directories in the scenery-pack if they are potential
      // overlay-tiles
      LOG.info(
          "Scanning your X-Plane installation for overlay-directories (this may take a moment)");
      scanBaseFolders.stream()
          .filter(this::isPotentialOverlayTilesDirectory)
          .forEach(overlayFolders::add);
    } else {
      // if using user-provided folders, all subfolders that contain an Earth nav data folder are
      // considered overlay-folders
      LOG.info("Retrieving overlay-directories from given overlay base-folders");
      for (Path baseFolder : scanBaseFolders) {
        try (final var stream = Files.walk(baseFolder)) {
          stream
              .filter(path -> Files.exists(path.resolve(EARTH_NAV_DATA)))
              .forEach(overlayFolders::add);
        } catch (IOException e) {
          throw new IllegalStateException(
              String.format("Failed to retrieve overlay-directories below %s", e));
        }
      }
    }
    LOG.info("{} overlay-folders have been identified in total", overlayFolders.size());
    return overlayFolders;
  }

  private Set<Path> getDsfFilesFromPath(final Path source) {
    try (final var stream = Files.walk(source)) {
      return stream
          .filter(
              path -> DSF_TILE_FILENAME_PATTERN.matcher(path.getFileName().toString()).matches())
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw ExceptionHelper.uncheck(e);
    }
  }

  private Set<Path> findOrthoDirectories() {
    LOG.info("Scanning your X-Plane installation for ortho-sceneries (this may take a moment)");
    final var orthos =
        sceneryDirectories.get().stream()
            .filter(this::isPotentialOrthoTilesDirectory)
            .collect(Collectors.toUnmodifiableSet());
    LOG.info("{} ortho-sceneries found in total", orthos.size());
    return orthos;
  }

  private boolean isPotentialOverlayTilesDirectory(final Path dir) {
    if (!Files.isDirectory(dir.resolve(EARTH_NAV_DATA))) {
      LOG.trace(
          "{} is not an overlay-folder as it does not contain an {} folder", dir, EARTH_NAV_DATA);
    }
    // may not contain any file that signals it is _not_ an overlay-scenery dir
    var excluder = firstMatchInFolder(dir, OVERLAY_DETECTION_EXCLUDERS);
    if (excluder.isPresent()) {
      LOG.trace("{} is NOT an overlay-folder as it contains excluder-file {}", dir, excluder.get());
      return false;
    }
    // well-known folder names that match overlay-scenery
    if (OVERLAY_FOLDER_NAME_PATTERNS.stream()
        .anyMatch(pattern -> pattern.matcher(dir.getFileName().toString()).matches())) {
      LOG.trace("{} is likely an overlay-folder due to it's directory-name", dir);
      return true;
    }
    // contained files that indicate overlay-scenery
    var includer = firstMatchInFolder(dir, OVERLAY_DETECTION_INCLUDERS);
    if (includer.isPresent()) {
      LOG.trace(
          "{} is likely an overlay-folder as it contains includer-file {}", dir, includer.get());
      return true;
    }
    LOG.trace("{} is NOT an overlay-folder as nothing indicates it was", dir);
    return false;
  }

  private boolean isPotentialOrthoTilesDirectory(final Path dir) {
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
      throw ExceptionHelper.uncheck(e);
    }
  }
}
