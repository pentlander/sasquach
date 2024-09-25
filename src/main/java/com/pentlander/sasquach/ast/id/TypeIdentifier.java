package com.pentlander.sasquach.ast.id;

import com.pentlander.sasquach.name.ModuleScopedTypeName;
import com.pentlander.sasquach.name.Name;
import com.pentlander.sasquach.name.UnqualifiedTypeName;

public sealed interface TypeIdentifier extends Identifier permits TypeId, ModuleScopedTypeId {
  @Override
  UnresolvedTypeName name();

  sealed interface UnresolvedTypeName extends Name permits ModuleScopedTypeName,
      UnqualifiedTypeName {}
}
