package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Declaration of a type alias, with optional type parameters. */
public record TypeAlias(Identifier id, List<TypeParameter> typeParameters,
                        TypeNode<Type> typeNode, Range range) implements TypeNode<Type>,
    NamedTypeDefintion {
  public TypeAlias {
    typeParameters = Objects.requireNonNullElse(typeParameters, List.of());
  }

  public Type type() {
    return typeNode.type();
  }

  @Override
  public String toPrettyString() {
    var typeParams =
        !typeParameters.isEmpty() ? typeParameters.stream().map(TypeNode::toPrettyString)
            .collect(Collectors.joining(", ", "[", "]")) : "";
    return "type %s%s = %s".formatted(id.name(), typeParams, typeNode.toPrettyString());
  }
}