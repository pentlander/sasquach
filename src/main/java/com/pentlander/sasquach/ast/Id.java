package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public sealed interface Id permits Identifier, QualifiedIdentifier {
  String name();
  Range.Single range();
}
