package com.pentlander.sasquach.type;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.TypeNode;

/**
 *
 * Represents a type parameter defined as part of a type alias or function signature.
 */
// TODO: Remove TypeNode interface impl
public record TypeParameter(Identifier id) implements TypeNode, NamedTypeDefinition {
  @Override
  public Type type() {
    return new TypeVariable(id.name().toString());
  }

  @Override
  public Range range() {
    return id.range();
  }
}
