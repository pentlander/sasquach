package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * An unqualified id.
 */
public record Id(String name, Range.Single range) implements Node, Identifier {
  @Override
  public String toString() {
    return "%s[%s]".formatted(name, range);
  }
}
