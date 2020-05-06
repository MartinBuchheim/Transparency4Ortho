package de.melb00m.tr4o.misc;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Class that allows to specify verifications that might trigger typical exceptions using an
 * expressive syntax.
 *
 * @author Martin Buchheim
 */
public final class Verify {

  private Verify() {}

  public static Verification withErrorMessage(
      final String message, final Supplier<?>... replacements) {
    return new Verification(message, replacements);
  }

  public static Verification withErrorMessage(final String message, final Object... replacements) {
    return new Verification(message, replacements);
  }

  public static void argument(final boolean isValid) {
    new Verification().argument(isValid);
  }

  public static void state(final boolean isValid) {
    new Verification().state(isValid);
  }

  public static void nonNull(final Object value) {
    new Verification().nonNull(value);
  }

  public static class Verification {
    private final Optional<String> message;
    private final Optional<Supplier<?>[]> messageSuppliers;
    private final Optional<Object[]> messageReplacements;

    private Verification() {
      this(null, null, null);
    }

    public Verification(
        final String message,
        final Supplier<?>[] messageSuppliers,
        final Object[] messageReplacements) {
      this.message = Optional.ofNullable(message);
      this.messageSuppliers = Optional.ofNullable(messageSuppliers);
      this.messageReplacements = Optional.ofNullable(messageReplacements);
    }

    private Verification(final String message, final Supplier<?>... suppliers) {
      this(message, suppliers, null);
    }

    private Verification(final String message, final Object... replacements) {
      this(message, null, replacements);
    }

    public void argument(final boolean isValid) {
      if (!isValid) {
        throw buildMessage()
            .map(IllegalArgumentException::new)
            .orElseGet(IllegalArgumentException::new);
      }
    }

    private Optional<String> buildMessage() {
      if (message.isPresent()) {
        if (messageReplacements.isPresent()) {
          return Optional.of(String.format(message.get(), messageReplacements.get()));
        }
        if (messageSuppliers.isPresent()) {
          return Optional.of(
              String.format(
                  message.get(),
                  Arrays.stream(messageSuppliers.get()).map(Supplier::get).toArray()));
        }
      }
      return message;
    }

    public void state(final boolean isValid) {
      if (!isValid) {
        throw buildMessage().map(IllegalStateException::new).orElseGet(IllegalStateException::new);
      }
    }

    public void nonNull(final Object value) {
      throw buildMessage().map(NullPointerException::new).orElseGet(NullPointerException::new);
    }
  }
}
