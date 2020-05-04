package de.melb00m.tr4o.helper;

import me.tongfei.progressbar.DelegatingProgressBarConsumer;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.logging.log4j.Logger;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OutputHelper {

  private static final Set<String> YES_SELECTION = Set.of("y", "yes");
  private static final Set<String> NO_SELECTION = Set.of("n", "no");

  private OutputHelper() {}

  public static ProgressBarBuilder getPreconfiguredBuilder() {
    return new ProgressBarBuilder().setStyle(ProgressBarStyle.ASCII).setUpdateIntervalMillis(250);
  }

  public static ProgressBarBuilder getPreconfiguredLoggedBuilder(final Logger logger) {
    return getPreconfiguredBuilder().setConsumer(new DelegatingProgressBarConsumer(logger::info));
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
  private static String readLineFromConsole(final String formatted, Object... replacements) {
    if (System.console() != null) {
      return System.console().readLine(formatted, replacements);
    }
    final var writer = new PrintWriter(System.out);
    writer.println(String.format(formatted, replacements));
    writer.flush();
    return new Scanner(System.in).nextLine();
  }

  @SuppressWarnings("java:S106")
  private static void writeLinesToConsole(final String... lines) {
    final var writer =
        System.console() != null ? System.console().writer() : new PrintWriter(System.out);
    Arrays.stream(lines).forEach(writer::println);
    writer.flush();
  }
}
