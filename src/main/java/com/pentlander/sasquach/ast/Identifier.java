package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * An unqualified id.
 */
public record Identifier(String name, Range.Single range) implements Node, Id {
  @Override
  public String toString() {
    return "%s[%s]".formatted(name, range);
  }
}
