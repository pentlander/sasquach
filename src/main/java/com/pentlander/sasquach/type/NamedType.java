package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Name;
import com.pentlander.sasquach.ast.TypeNode;
import java.util.List;

/** Unresolved type referred to by captureName. **/
public sealed interface NamedType extends Type permits LocalNamedType, ModuleNamedType {
  Identifier id();

  default Name typeName() {
    return id().name();
  }

  List<TypeNode> typeArgumentNodes();

  default List<Type> typeArguments() {
    return typeArgumentNodes().stream().map(TypeNode::type).toList();
  }
}

