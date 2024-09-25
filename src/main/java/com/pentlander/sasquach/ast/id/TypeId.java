package com.pentlander.sasquach.ast.id;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.name.UnqualifiedTypeName;

/**
 * An unqualified id.
 */
public record TypeId(UnqualifiedTypeName name, Range.Single range) implements Node,
    TypeIdentifier {
  public TypeId(String name, Range.Single range) {
    this(new UnqualifiedTypeName(name), range);
  }

  @Override
  public String toString() {
    return "%s[%s]".formatted(name, range);
  }
}
