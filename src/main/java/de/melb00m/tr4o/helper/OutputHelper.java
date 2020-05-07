package de.melb00m.tr4o.helper;

import de.melb00m.tr4o.app.Transparency4Ortho;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.logging.log4j.Level;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Helpers for handling input and output for the command line.
 *
 * @author Martin Buchheim
 */
public final class OutputHelper {

  private OutputHelper() {}

  public static <T> Stream<T> maybeShowWithProgressBar(
      final String taskName,
      final Stream<T> stream,
      final Level threshold,
      final Transparency4Ortho command) {
    if (command.getConsoleLogLevel().isMoreSpecificThan(threshold)) {
      return ProgressBar.wrap(stream, getProgressBarBuilder().setTaskName(taskName));
    }
    return stream;
  }

  public static ProgressBarBuilder getProgressBarBuilder() {
    return new ProgressBarBuilder().setStyle(ProgressBarStyle.ASCII).setUpdateIntervalMillis(250);
  }

  public static String bytesToHex(final byte[] bytes) {
    return IntStream.range(0, bytes.length)
        .map(idx -> bytes[idx] & 0xff)
        .mapToObj(in -> String.format("%02x", in))
        .collect(Collectors.joining());
  }
}
