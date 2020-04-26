package de.melb00m.tr4o.util;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class LazyProperty<T> {

  private final Supplier<T> supplier;
  private final AtomicReference<T> ref;

  public LazyProperty(Supplier<T> supplier) {
    this.supplier = supplier;
    this.ref = new AtomicReference<>();
  }

  public T get() {
    synchronized (this) {
      if (null == ref.get()) {
        ref.set(supplier.get());
      }
    }
    return ref.get();
  }
}
