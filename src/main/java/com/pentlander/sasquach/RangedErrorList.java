package com.pentlander.sasquach;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record RangedErrorList(List<RangedError> errors) {
  private static final RangedErrorList EMPTY = new RangedErrorList(List.of());

  public static RangedErrorList empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return errors.isEmpty();
  }

  public void throwIfNotEmpty(Sources sources) throws CompilationException {
    if (!isEmpty()) {
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

    public Builder addAll(Collection<RangedError> rangedError) {
      errors.addAll(rangedError);
      return this;
    }

    public Builder addAll(RangedErrorList rangedErrors) {
      errors.addAll(rangedErrors.errors);
      return this;
    }

    public RangedErrorList build() {
      return new RangedErrorList(List.copyOf(errors));
    }
  }
}
