package com.pentlander.sasquach.backend;

public sealed interface Opt<T> {
  record Some<T>(T value) implements Opt<T> {}
  final class None<T>implements Opt<T>{}

  default T get(Opt<T> opt) {
    return switch (opt) {
      case Some<T> some && some.value() != null -> some.value();
      case Some<T> some -> some.value();
      case None<?> ignored -> null;
    };
  }
}
