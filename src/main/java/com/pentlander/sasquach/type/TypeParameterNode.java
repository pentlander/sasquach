package com.pentlander.sasquach.type;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.id.TypeParameterId;
import com.pentlander.sasquach.name.UnqualifiedTypeName;

/**
 *
 * Represents a type parameter defined as part of a type alias or function signature.
 */
public record TypeParameterNode(TypeParameterId id) implements Node, NamedTypeDefinition {
  public UnqualifiedTypeName name() {
    return id.name();
  }

  public UniversalType toUniversal() {
    return new UniversalType(id.name().toString());
  }

  public TypeVariable toTypeVariable(int level) {
    return new TypeVariable(id.name().toString(), level, id);
  }

  public TypeParameter toTypeParameter() {
    return new TypeParameter(name());
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
