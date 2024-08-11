package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public sealed interface Identifier permits Id, ModuleScopedTypeId, QualifiedModuleId, TypeId {
  Name name();

  Range.Single range();
}
