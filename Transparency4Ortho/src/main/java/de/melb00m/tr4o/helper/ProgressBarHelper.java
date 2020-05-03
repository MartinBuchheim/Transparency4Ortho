package de.melb00m.tr4o.helper;

import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.logging.log4j.Logger;

public final class ProgressBarHelper {

  private ProgressBarHelper() {}

  public static ProgressBarBuilder getPrecondiguredBuilder() {
    return new ProgressBarBuilder().setStyle(ProgressBarStyle.ASCII).setUpdateIntervalMillis(250);
  }

  public static ProgressBarBuilder getPreconfiguredLoggedBuilder(final Logger logger) {
    return getPrecondiguredBuilder().setConsumer(new DelegatingProgressBarConsumer(logger::info));
  }
}
