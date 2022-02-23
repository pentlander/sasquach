package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range.Single;

/** Identifier that is qualified by a module name. */
public record ModuleScopedIdentifier(Identifier moduleId, Identifier id) implements Id {
  @Override
  public String name() {
    return moduleId.name() + "." + id.name();
  }

  @Override
  public Single range() {
    return (Single) moduleId.range().join(id.range());
  }
}
