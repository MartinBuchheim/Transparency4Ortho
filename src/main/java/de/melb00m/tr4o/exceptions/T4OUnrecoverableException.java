package de.melb00m.tr4o.exceptions;

/**
 * {@link RuntimeException}-subclass that wraps another (typically checked) exception.
 *
 * @see Exceptions#unrecoverable(Throwable)
 * @author Martin Buchheim
 */
public class T4OUnrecoverableException extends RuntimeException {

  private final Throwable throwable;

  public T4OUnrecoverableException(final Throwable throwable) {
    this(throwable.getMessage(), throwable);
  }

  public T4OUnrecoverableException(final String message, final Throwable throwable) {
    super(message, throwable);
    this.throwable = throwable;
  }

  public Throwable getThrowable() {
    return throwable;
  }
}
