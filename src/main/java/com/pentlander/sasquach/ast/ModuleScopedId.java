package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range.Single;

/** Identifier that is qualified by a module captureName. */
public record ModuleScopedId(Id moduleId, Id id) implements Identifier {
  @Override
  public String name() {
    return moduleId.name() + "." + id.name();
  }

  @Override
  public Single range() {
    return  moduleId.range().join(id.range());
  }
}
