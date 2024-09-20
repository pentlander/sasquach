package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public sealed interface Identifier permits Id, TypeIdentifier, QualifiedModuleId {
  Name name();

  Range.Single range();
}
