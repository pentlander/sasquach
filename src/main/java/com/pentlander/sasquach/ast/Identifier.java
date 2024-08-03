package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public sealed interface Identifier permits Id, ModuleScopedId, QualifiedModuleId {
  String name();

  Range.Single range();
}
