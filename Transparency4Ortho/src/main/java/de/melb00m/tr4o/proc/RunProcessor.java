package de.melb00m.tr4o.proc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RunProcessor {

  private static final Logger LOG = LogManager.getLogger(RunProcessor.class);

  public void startProcessing() {
    synchronized (RunProcessor.class) {
      new LibraryGenerator().validateOrCreateLibrary();
      new OverlayScanner().scanAndProcessOverlays();
    }
  }

}
