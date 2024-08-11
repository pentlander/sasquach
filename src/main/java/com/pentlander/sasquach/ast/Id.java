package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * An unqualified id.
 */
public record Id(UnqualifiedName name, Range.Single range) implements Node, Identifier {
  public Id(String name, Range.Single range) {
    this(new UnqualifiedName(name), range);
  }

  @Override
  public String toString() {
    return "%s[%s]".formatted(name, range);
  }
}
