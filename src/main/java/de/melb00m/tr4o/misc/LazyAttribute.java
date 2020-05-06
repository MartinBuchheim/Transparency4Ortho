package de.melb00m.tr4o.misc;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Class that allows for lazy-loaded attributes that are initialized when first fetched.
 *
 * @param <T> Wrapped attribute type
 * @author Martin Buchheim
 */
public class LazyAttribute<T> {

  private final AtomicReference<T> reference;
  private final Supplier<T> supplier;

  public LazyAttribute(Supplier<T> supplier) {
    this.reference = new AtomicReference<>();
    this.supplier = supplier;
  }

  public T get() {
    synchronized (reference) {
      if (null == reference.get()) {
        reference.set(supplier.get());
      }
    }
    return reference.get();
  }
}
