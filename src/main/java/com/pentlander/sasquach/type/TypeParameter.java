package com.pentlander.sasquach.type;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.Node;

/**
 *
 * Represents a type parameter defined as part of a type alias or function signature.
 */
public record TypeParameter(Identifier id) implements Node, NamedTypeDefinition {
  public String name() {
    return id.name().toString();
  }

  public UniversalType toUniversal(int level) {
    return new UniversalType(id.name().toString(), level);
  }

  public TypeVariable toTypeVariable(int level) {
    return new TypeVariable(id.name().toString(), level, id);
  }

  @Override
  public Range range() {
    return id.range();
  }

  @Override
  public String toPrettyString() {
    return id.name().toString();
  }
}
