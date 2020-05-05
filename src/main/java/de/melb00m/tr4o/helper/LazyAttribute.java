package de.melb00m.tr4o.helper;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
