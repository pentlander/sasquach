package com.pentlander.sasquach.type;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.NamedTypeDefintion;
import com.pentlander.sasquach.ast.TypeNode;

/**
 * Represents a type parameter defined as part of a type alias or function signature.
 */
public record TypeParameter(Id id) implements TypeNode<Type>, NamedTypeDefintion {
  @Override
  public Type type() {
    return new TypeVariable(id.name());
  }

  @Override
  public Range range() {
    return id.range();
  }
}
