package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public sealed interface Id permits Identifier, ModuleScopedIdentifier, QualifiedIdentifier {
  String name();
  Range.Single range();
}
