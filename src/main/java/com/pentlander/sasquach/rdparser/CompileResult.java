package com.pentlander.sasquach.rdparser;

import com.pentlander.sasquach.RangedErrorList;

public record CompileResult<T>(T item, RangedErrorList errors) {
  public static <T> CompileResult<T> of(T item, RangedErrorList errors) {
    return new CompileResult<>(item, errors);
  }

  public boolean hasError() {
    return !errors.isEmpty();
  }
}
