package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.ast.typenode.TypeStatement;
import com.pentlander.sasquach.type.TypeParameterNode;

/** Location where {@link com.pentlander.sasquach.type.NamedType} is defined. **/
public sealed interface NamedTypeDefinition permits ForeignClass, TypeStatement, TypeParameterNode {
  record ForeignClass(Class<?> clazz) implements NamedTypeDefinition {}
}
