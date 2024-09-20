package com.pentlander.sasquach.ast;

public sealed interface TypeIdentifier extends Identifier permits TypeId, ModuleScopedTypeId {
  @Override
  UnresolvedTypeName name();

  sealed interface UnresolvedTypeName extends Name permits ModuleScopedTypeName, UnqualifiedTypeName {}
}
