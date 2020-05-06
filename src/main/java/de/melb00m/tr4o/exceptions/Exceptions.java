package de.melb00m.tr4o.exceptions;

/**
 * Class containing utilties for easier exception-handling, especially of checked exceptions.
 *
 * @author Martin Buchheim
 */
public final class Exceptions {

  private Exceptions() {}

  /**
   * Method that wraps any method into an {@link T4OUnrecoverableException} and throws it.
   *
   * <p>Especially useful for wrapping and throwing checked exceptions which the application cannot
   * recover from.
   *
   * @param throwable Throwable to wrap
   * @param <T> The throwable type
   * @return Nothing, an exception will always be thrown. This is only in the signature to allow
   *     {@code throw ExceptionHelper.unrecoverable(...)}-statements.
   * @throws T4OUnrecoverableException Wrapping the original throwable
   */
  public static <T extends Throwable> T4OUnrecoverableException unrecoverable(T throwable) {
    throw new T4OUnrecoverableException(throwable);
  }

  /**
   * Sneaky method that tricks the compiler into passing checked exceptions through methods that
   * don't explicitly handle or declare them.
   *
   * <p>Unlike wrapping them into a {@link RuntimeException}, the checked exception stays intact and
   * can be handled further down the line by clients that expect them.
   *
   * @param throwable Throwable to pass on
   * @param <T> The throwable type
   * @return Nothing, an exception will always be thrown. This is only in the signature to allow *
   *     {@code throw ExceptionHelper.sneakyUncheck(...)}-statements.
   * @throws T Original checked exception invisible to compiler
   */
  @SuppressWarnings("unchecked")
  public static <T extends Throwable> RuntimeException sneakyUncheck(T throwable) throws T {
    throw (T) throwable;
  }
}
