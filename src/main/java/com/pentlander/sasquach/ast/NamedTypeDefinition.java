package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.NamedTypeDefinition.ForeignClass;
import com.pentlander.sasquach.type.TypeParameter;

public sealed interface NamedTypeDefinition permits ForeignClass, TypeAlias, TypeParameter {
  record ForeignClass(Class<?> clazz) implements NamedTypeDefinition {}
}
