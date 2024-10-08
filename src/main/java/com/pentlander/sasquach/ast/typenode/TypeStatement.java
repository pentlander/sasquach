package com.pentlander.sasquach.ast.typenode;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.NamedTypeDefinition;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.id.TypeId;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeParameterNode;
import java.util.List;
import java.util.stream.Collectors;

/** Declaration of a type alias, with optional type parameters. */
public record TypeStatement(TypeId id, List<TypeParameterNode> typeParameterNodes, TypeNode typeNode, boolean isAlias,
                            Range range) implements TypeNode, NamedTypeDefinition {

  public Type type() {
    return typeNode.type();
  }

  @Override
  public String toPrettyString() {
    var typeParams =
        !typeParameterNodes.isEmpty() ? typeParameterNodes.stream().map(Node::toPrettyString)
            .collect(Collectors.joining(", ", "[", "]")) : "";
    return "type %s%s = %s".formatted(id.name(), typeParams, typeNode.toPrettyString());
  }
}
