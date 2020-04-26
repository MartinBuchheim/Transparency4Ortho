package de.melb00m.tr4o.proc;

import de.melb00m.tr4o.app.RunConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

public class RunProcessor {

    private static final Logger LOG = LogManager.getLogger(RunProcessor.class);
    private static final String LIB_COPY_FROM = "Resources/default scenery/1000 roads";
    private static final String LIB_COPY_TO = "Resources/1000_roads";
    private static final Set<String> LIB_EXPORTED_FILES = Set.of("roads.net", "roads_EU.net");

    private final RunConfiguration config;

    public RunProcessor(RunConfiguration config) {
        this.config = config;
    }

    public void startProcessing() {
        synchronized (this) {
            LOG.debug("Using configuration {}", config);

//            final var pbb = new ProgressBarBuilder().setConsumer(new DelegatingProgressBarConsumer(LOG::debug)).setStyle(ProgressBarStyle.ASCII).setTaskName("Counting Stuff");
//          ProgressBar.wrap(IntStream.range(1, 100), pbb).forEach(i -> {
//            try {
//              Thread.sleep(100);
//            } catch (InterruptedException e) {
//              e.printStackTrace();
//            }
//          });

            // generateLibrary();
            new XPToolsInterface(config.getXpToolsPath(), true, config.getTemporariesBasePath()).getDSFExecutable();
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
