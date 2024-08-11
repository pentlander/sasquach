package com.pentlander.sasquach;


import org.jspecify.annotations.Nullable;

public abstract class AbstractRangedError extends RuntimeException implements RangedError {
  protected Range range;

  protected AbstractRangedError(String message, Range range, @Nullable Throwable cause) {
    super(message, cause);
    this.range = range;
  }

  @Override
  public final Range range() {
    return range;
  }

  @Override
  abstract public String toPrettyString(Source source);
}
