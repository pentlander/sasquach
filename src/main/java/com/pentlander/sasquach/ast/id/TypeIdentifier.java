package com.pentlander.sasquach.ast.id;

import com.pentlander.sasquach.name.TypeName;

public sealed interface TypeIdentifier extends Identifier permits TypeId, TypeParameterId {
  TypeName name();
}
