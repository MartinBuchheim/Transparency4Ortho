package de.melb00m.tr4o.helper;

import de.melb00m.tr4o.app.Transparency4Ortho;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.logging.log4j.Level;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class OutputHelper {

  private static final Set<String> YES_SELECTION = Set.of("y", "yes");
  private static final Set<String> NO_SELECTION = Set.of("n", "no");

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

  public static List<String> joinLinesByTotalLength(
      final int lineLength, final String joiner, final Collection<String> values) {
    final var outLines = new ArrayList<String>();
    final var queue = new LinkedList<>(values);
    var lineBuilder = new StringBuilder();
    while (!queue.isEmpty()) {
      var next = queue.poll();
      if (lineBuilder.length() + joiner.length() + next.length() > lineLength) {
        var finishedLine = lineBuilder.toString();
        if (!finishedLine.isBlank()) outLines.add(finishedLine);
        lineBuilder = new StringBuilder(next);
      } else {
        if (lineBuilder.length() > 0) lineBuilder.append(joiner);
        lineBuilder.append(next);
      }
    }
    if (lineBuilder.length() > 0) outLines.add(lineBuilder.toString());
    return outLines;
  }

  public static void confirmYesNo(
      final boolean noIsDefault,
      final Supplier<String> question,
      final Consumer<String> onNoSelection) {
    final var response =
        readLineFromConsole(
            "%s [%s/%s]", question.get(), noIsDefault ? "y" : "Y", noIsDefault ? "N" : "n");
    if (response.isBlank()) {
      if (noIsDefault) {
        onNoSelection.accept(response);
      }
      return;
    } else if (YES_SELECTION.contains(response.toLowerCase())) {
      return;
    } else if (NO_SELECTION.contains(response.toLowerCase())) {
      onNoSelection.accept(response);
    }
    confirmYesNo(noIsDefault, question, onNoSelection);
  }

  public static void confirmYesOrExit(
      final boolean noIsDefault,
      final Supplier<String> question,
      final Supplier<String> exitMessage) {
    confirmYesNo(
        noIsDefault,
        question,
        response -> {
          final var message = exitMessage.get();
          if (null != message && !message.isBlank()) {
            writeLinesToConsole(message);
          }
          System.exit(0);
        });
  }

  @SuppressWarnings("java:S106")
  public static String readLineFromConsole(final String formatted, Object... replacements) {
    if (System.console() != null) {
      return System.console().readLine(formatted, replacements);
    }
    writeLinesToConsole(String.format(formatted, replacements));
    return new Scanner(System.in).nextLine();
  }

  @SuppressWarnings("java:S106")
  public static void writeLinesToConsole(final String... lines) {
    final var writer =
        System.console() != null ? System.console().writer() : new PrintWriter(System.out);
    Arrays.stream(lines).forEach(writer::println);
    writer.flush();
  }
}
