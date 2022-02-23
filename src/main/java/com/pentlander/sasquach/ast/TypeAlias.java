package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameter;
import java.util.List;
import java.util.Objects;

/** Declaration of a type alias, with optional type parameters. */
public record TypeAlias(Identifier id, List<TypeNode<TypeParameter>> typeParameterNodes,
                        TypeNode<Type> typeNode, Range range) implements Node {
  public TypeAlias {
    typeParameterNodes = Objects.requireNonNullElse(typeParameterNodes, List.of());
  }

  public List<TypeParameter> typeParameters() {
    return typeParameterNodes.stream().map(TypeNode::type).toList();
  }

  public Type type() {
    return typeNode.type();
  }

  @Override
  public String toPrettyString() {
    return "type %s = %s".formatted(id.name(), typeNode.toPrettyString());
  }
}