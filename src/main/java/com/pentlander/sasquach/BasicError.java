package com.pentlander.sasquach;

/**
 * Error message that only contains the message.
 */
public record BasicError(String message, Range range) implements RangedError {

  @Override
  public String toPrettyString(Source source) {
    return """
        %s
        %s
        """.formatted(message, source.highlight(range));
  }
}
