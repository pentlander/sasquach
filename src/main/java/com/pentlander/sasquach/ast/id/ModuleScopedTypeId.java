package com.pentlander.sasquach.ast.id;

import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.name.ModuleScopedTypeName;

/** Identifier that is qualified by a module name. */
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
