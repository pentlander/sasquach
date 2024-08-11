package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range.Single;

/** Identifier that is qualified by a module captureName. */
public record ModuleScopedTypeId(Id moduleId, TypeId id) implements Identifier {
  @Override
  public ModuleScopedTypeName name() {
    return new ModuleScopedTypeName(moduleId.name(), id.name());
  }

  @Override
  public Single range() {
    return  moduleId.range().join(id.range());
  }
}
