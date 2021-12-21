package com.pentlander.sasquach;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record RangedErrorList(List<RangedError> errors) {
  private static final RangedErrorList EMPTY = new RangedErrorList(List.of());

  public static RangedErrorList empty() {
    return EMPTY;
  }

  public void throwIfNotEmpty(Source source) throws CompilationException {
    if (!errors.isEmpty()) {
      throw new CompilationException(source, errors);
    }
  }

  public void throwIfNotEmpty(Sources sources) throws CompilationException {
    if (!errors.isEmpty()) {
      throw new CompilationException(sources, errors);
    }
  }

  public RangedErrorList concat(RangedErrorList other) {
    var mergedErrors = new ArrayList<>(this.errors);
    mergedErrors.addAll(other.errors);
    return new RangedErrorList(mergedErrors);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final List<RangedError> errors = new ArrayList<>();

    private Builder() {}

    public Builder add(RangedError rangedError) {
      errors.add(rangedError);
      return this;
    }

    public RangedErrorList build() {
      return new RangedErrorList(List.copyOf(errors));
    }
  }
}
