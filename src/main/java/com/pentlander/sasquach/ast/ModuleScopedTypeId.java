package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range.Single;

/** Identifier that is qualified by a module captureName. */
public record ModuleScopedTypeId(TypeId moduleId, TypeId id) implements TypeIdentifier {
  @Override
  public ModuleScopedTypeName name() {
    return new ModuleScopedTypeName(moduleId.name().toName(), id.name());
  }

  @Override
  public Single range() {
    return  moduleId.range().join(id.range());
  }
}
