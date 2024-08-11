package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/**
 * An unqualified id.
 */
public record TypeId(UnqualifiedTypeName name, Range.Single range) implements Node, Identifier {
  public TypeId(String name, Range.Single range) {
    this(new UnqualifiedTypeName(name), range);
  }

  @Override
  public String toString() {
    return "%s[%s]".formatted(name, range);
  }

  public Id toId() {
    return new Id(new UnqualifiedName(name.value()), range);
  }
}
