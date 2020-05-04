package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.AppConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OverlayTileTransformer {

  private static final Logger LOG = LogManager.getLogger(OverlayTileTransformer.class);
  private static final Set<Pattern> REPLACEMENT_PATTERNS =
      Set.of(
          Pattern.compile(
              AppConfig.getApplicationConfig()
                  .getString("overlay-scanner.replacements.roads-net.pattern")),
          Pattern.compile(
              AppConfig.getApplicationConfig()
                  .getString("overlay-scanner.replacements.roads_EU-net.pattern")));

  private final Path dsfFile;
  private final Path backupDsfFile;
  private final String libraryName;
  private final XPToolsInterface xpToolsInterface;

  private boolean transformed;

  OverlayTileTransformer(
      Path dsfFile, Path backupFolder, String libraryName, XPToolsInterface xpToolsInterface) {
    this.dsfFile = dsfFile;
    this.backupDsfFile =
        backupFolder.resolve(
            dsfFile.getParent().getParent().getParent().getParent().relativize(dsfFile));
    this.libraryName = libraryName;
    this.xpToolsInterface = xpToolsInterface;
  }

  public static Set<OverlayTileTransformer> buildTransformers(final Set<Path> overlayTiles) {
    final var backupFolder =
        AppConfig.getRunArguments()
            .getBackupPath()
            .orElseGet(
                () ->
                    Path.of(
                        AppConfig.getApplicationConfig()
                            .getString("overlay-scanner.backup-folder")))
            .resolve(new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()));
    final var libraryName =
        AppConfig.getApplicationConfig().getString("overlay-scanner.library-prefix");
    final var xpTools = new XPToolsInterface();
    return overlayTiles.stream()
        .map(tile -> new OverlayTileTransformer(tile, backupFolder, libraryName, xpTools))
        .collect(Collectors.toUnmodifiableSet());
  }

  void runTransformation() {
    synchronized (this) {
      try {
        LOG.trace("Analyzing Overlay tile: {}", dsfFile);
        final var sourceContent = xpToolsInterface.dsfToText(dsfFile);
        final var transformedContent =
            sourceContent
                .lines()
                .map(this::searchAndTransformLine)
                .collect(Collectors.joining(System.lineSeparator()));

        // shortcut: nothing had to be replaced
        if (Objects.equals(sourceContent, transformedContent)) {
          LOG.trace("No modifications in Overlay file necessary: {}", dsfFile);
          return;
        }
        backupOriginalDsfFile();
        LOG.trace("Replacing overlay with transformed version: {}", dsfFile);
        Files.delete(dsfFile);
        xpToolsInterface.textToDsf(dsfFile, transformedContent);
        transformed = true;
      } catch (Exception e) {
        throw new IllegalStateException(
            String.format("Transformation of Overlay failed: %s", dsfFile), e);
      }
    }
  }

  private void backupOriginalDsfFile() throws IOException {
    LOG.trace("Creating backup of original overlay '{} in '{}'", dsfFile, backupDsfFile);
    synchronized (OverlayTileTransformer.class) {
      Files.createDirectories(backupDsfFile.getParent());
    }
    Files.copy(dsfFile, backupDsfFile);
  }

  private String searchAndTransformLine(final String input) {
    for (Pattern pattern : REPLACEMENT_PATTERNS) {
      var matcher = pattern.matcher(input);
      if (matcher.matches()) {
        return matcher.replaceFirst("${before}" + libraryName + "${after}");
      }
    }
    return input;
  }

  public boolean isTransformed() {
    return transformed;
  }

  public Path getDsfFile() {
    return dsfFile;
  }

  public Path getBackupDsfFile() {
    return backupDsfFile;
  }
}
