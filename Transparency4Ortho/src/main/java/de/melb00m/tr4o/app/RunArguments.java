package de.melb00m.tr4o.app;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.logging.log4j.Level;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Class holding the configuration for this run.
 *
 * @author martin.buchheim
 */
public final class RunArguments {

  static final String DEFAULT_LIBRARY_FOLDER = "aaa_TransparentRoads4Ortho";
  static final String DEFAULT_LIBRARY_PREFIX = "transparentRoads4Ortho";

  private final Path xPlanePath;
  private final Set<Path> overlaysPath;
  private final Optional<Path> xpToolsPath;
  private final Level consoleLogLevel;

  public RunArguments(
      Path xPlanePath,
      Set<Path> overlaysPath,
      Optional<Path> xpToolsPath,
      Level consoleLogLevel) {
    this.xPlanePath = xPlanePath;
    this.overlaysPath = overlaysPath;
    this.xpToolsPath = xpToolsPath;
    this.consoleLogLevel = consoleLogLevel;
  }

  public Path getXPlanePath() {
    return xPlanePath;
  }

  public Set<Path> getOverlayPaths() {
    return overlaysPath;
  }

  public Optional<Path> getXpToolsPath() {
    return xpToolsPath;
  }

  public Level getConsoleLogLevel() {
    return consoleLogLevel;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
        .append("xPlanePath", xPlanePath)
        .append("overlaysPath", overlaysPath)
        .append("xpToolsPath", xpToolsPath)
        .toString();
  }
}
