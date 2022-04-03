package com.pentlander.sasquach;

public interface Mergable<T extends Mergable<T>> {
  T merge(T other);
}
