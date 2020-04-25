package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.TransparentRoads4OrthoConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

public class RunProcessor {

  private static final Logger LOG = LogManager.getLogger(RunProcessor.class);
  private static final String LIB_COPY_FROM = "Resources/default scenery/1000 roads";
  private static final String LIB_COPY_TO = "Resources/1000_roads";
  private static final Set<String> LIB_EXPORTED_FILES = Set.of("roads.net", "roads_EU.net");

  private final TransparentRoads4OrthoConfig config;

  public RunProcessor(TransparentRoads4OrthoConfig config) {
    this.config = config;
  }

  public void startProcessing() {
    synchronized (this) {
      LOG.debug("Using configuration {}", config);
      // generateLibrary();
      new DSFToolInterface(config.getDsfToolPath(), true, config.getTempDir()).getDSFExecutable();
    }
  }

  private void generateLibrary() {
    var generator =
        new LibraryGenerator(
            config.getLibraryPrefix(),
            config.getLibraryPath(),
            config.getLibraryPath().resolve(LIB_COPY_TO),
            config.getXPlanePath().resolve(LIB_COPY_FROM),
            LIB_EXPORTED_FILES);
    generator.validateOrCreateLibrary();
  }
}
