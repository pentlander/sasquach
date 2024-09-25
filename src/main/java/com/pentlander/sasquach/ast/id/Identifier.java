package com.pentlander.sasquach.ast.id;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.name.Name;

public sealed interface Identifier permits Id, TypeIdentifier, QualifiedModuleId {
  Name name();

  Range.Single range();
}
