package com.pentlander.sasquach.runtime;

public abstract class EqHashWrapper<T> {
  private final T item;

  public EqHashWrapper(T item) {
    this.item = item;
  }

  public abstract boolean equals(T structA, T structB);

  public abstract int hashCode(T struct);

  @Override
  public boolean equals(Object o) {
    if (!item.getClass().isInstance(o)) {
      return false;
    }
    //noinspection unchecked
    return equals(item, (T) o);
  }

  @Override
  public int hashCode() {
    return hashCode(item);
  }

  static final class Foo extends EqHashWrapper<String> {
    private final StructBase module;

    public Foo(StructBase module, String item) {
      super(item);
      this.module = module;
    }

    @Override
    public boolean equals(String structA, String structB) {
      return false;
    }

    @Override
    public int hashCode(String struct) {
      return 0;
    }
  }
}
