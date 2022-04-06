package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.TypeNode;
import java.util.List;

public sealed interface NamedType extends Type permits LocalNamedType, ModuleNamedType {
  Id id();

  List<TypeNode> typeArgumentNodes();

  default List<Type> typeArguments() {
    return typeArgumentNodes().stream().map(TypeNode::type).toList();
  }

}

