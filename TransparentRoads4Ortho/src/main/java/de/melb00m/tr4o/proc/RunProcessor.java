package de.melb00m.tr4o.proc;

import com.google.common.collect.ImmutableSet;
import de.melb00m.tr4o.app.TransparentRoads4OrthoConfig;
import lombok.val;

public class RunProcessor {

  private static final String LIB_COPY_FROM = "Resources/default scenery/1000 roads";
  private static final String LIB_COPY_TO = "Resources/1000_roads";
  private static final ImmutableSet<String> LIB_EXPORTED_FILES =
      ImmutableSet.of("roads.net", "roads_EU.net");

  private final TransparentRoads4OrthoConfig config;

  public RunProcessor(TransparentRoads4OrthoConfig config) {
    this.config = config;
  }

  public void startProcessing() {
    synchronized (this) {
      generateLibrary();
    }
  }

  private void generateLibrary() {
      val generator = new LibraryGenerator(
              config.getLibraryPrefix(),
              config.getLibraryPath(),
              config.getLibraryPath().resolve(LIB_COPY_TO),
              config.getXPlanePath().resolve(LIB_COPY_FROM),
              LIB_EXPORTED_FILES);
      generator.validateOrCreateLibrary();
  }
}
