package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.List;
import java.util.stream.Collectors;

/** Declaration of a type alias, with optional type parameters. */
public record TypeStatement(TypeId id, List<TypeParameter> typeParameters, TypeNode typeNode, boolean isAlias,
                            Range range) implements TypeNode, NamedTypeDefinition {

  public Type type() {
    return typeNode.type();
  }

  @Override
  public String toPrettyString() {
    var typeParams =
        !typeParameters.isEmpty() ? typeParameters.stream().map(Node::toPrettyString)
            .collect(Collectors.joining(", ", "[", "]")) : "";
    return "type %s%s = %s".formatted(id.name(), typeParams, typeNode.toPrettyString());
  }
}
