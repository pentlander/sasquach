package com.pentlander.sasquach.ast.id;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.name.QualifiedTypeName;

/**
 * An unqualified id.
 */
public record TypeId(QualifiedTypeName name, Range.Single range) implements Node,
    TypeIdentifier {

  @Override
  public String toString() {
    return "%s[%s]".formatted(name, range);
  }
}
