package de.melb00m.tr4o.overlay;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.xptools.XPToolsInterface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OverlayTileTransformer implements Runnable {

  private static final Logger LOG = LogManager.getLogger(OverlayTileTransformer.class);
  private static final Set<Pattern> REPLACEMENT_PATTERNS =
      Transparency4Ortho.CONFIG.getStringList("tiletransformer.replacements.patterns").stream()
          .map(Pattern::compile)
          .collect(Collectors.toUnmodifiableSet());

  private final Path dsfFile;
  private final Path backupDsfFile;
  private final String libraryName;
  private final XPToolsInterface xpToolsInterface;

  private boolean transformed;

  public OverlayTileTransformer(
      Path dsfFile, Path backupFolder, String libraryName, XPToolsInterface xpToolsInterface) {
    this.dsfFile = dsfFile;
    this.backupDsfFile =
        backupFolder.resolve(
            dsfFile.getParent().getParent().getParent().getParent().relativize(dsfFile));
    this.libraryName = libraryName;
    this.xpToolsInterface = xpToolsInterface;
  }

  @Override
  public void run() {
    synchronized (this) {
      try {
        LOG.trace("Analyzing overlay tile: {}", dsfFile);
        final var sourceContent = xpToolsInterface.dsfToText(dsfFile);
        final var transformedContent =
            sourceContent
                .lines()
                .map(this::searchAndTransformLine)
                .collect(Collectors.joining(System.lineSeparator()));

        // shortcut: nothing had to be replaced
        if (Objects.equals(sourceContent, transformedContent)) {
          LOG.trace("No modifications in overlay tile necessary: {}", dsfFile);
          return;
        }
        backupOriginalDsfFile();
        LOG.trace("Replacing overlay with transformed version: {}", dsfFile);
        Files.delete(dsfFile);
        xpToolsInterface.textToDsf(dsfFile, transformedContent);
        transformed = true;
      } catch (Exception e) {
        throw new IllegalStateException(
            String.format("Transformation of overlay failed: %s", dsfFile), e);
      }
    }
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

  private void backupOriginalDsfFile() throws IOException {
    LOG.trace("Creating backup of original overlay '{} in '{}'", dsfFile, backupDsfFile);
    synchronized (OverlayTileTransformer.class) {
      Files.createDirectories(backupDsfFile.getParent());
    }
    Files.copy(dsfFile, backupDsfFile);
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
