package com.pentlander.sasquach;


import org.jspecify.annotations.Nullable;

public abstract class AbstractRangedError extends RuntimeException implements RangedError {
  protected Range range;

  protected AbstractRangedError(String message, Range range, @Nullable Throwable cause) {
    super(message, cause);
    this.range = range;
  }

  protected AbstractRangedError(Range range, @Nullable Throwable cause) {
    super(cause);
    this.range = range;
  }

  @Override
  public final Range range() {
    return range;
  }

  @Override
  public String toPrettyString(Source source) {
    return """
          %s
          %s
          """.formatted(getMessage(), source.highlight(range));
  }
}
