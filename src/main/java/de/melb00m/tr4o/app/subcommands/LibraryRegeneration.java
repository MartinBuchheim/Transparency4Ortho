package de.melb00m.tr4o.app.subcommands;

import de.melb00m.tr4o.app.Transparency4Ortho;
import de.melb00m.tr4o.library.LibraryGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LibraryRegeneration implements Runnable {

  private static final Logger LOG = LogManager.getLogger(LibraryRegeneration.class);

  private final LibraryGenerator libraryGenerator;

  public LibraryRegeneration(final Transparency4Ortho command) {
    this.libraryGenerator = new LibraryGenerator(command);
  }

  @Override
  public void run() {
    LOG.info("Library re-generation started");
    libraryGenerator.regenerateLibrary();
    LOG.info("Library re-generation complete");
  }
}
