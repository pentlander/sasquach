package com.pentlander.sasquach.nameres;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.id.Identifier;

record DuplicateNameError(Identifier identifier, Identifier existingIdentifier) implements
    RangedError {
  @Override
  public Range range() {
    return identifier.range();
  }

  @Override
  public String toPrettyString(Source source) {
    return """
        Name '%s' already bound in scope.
        %s
        note: bound here
        %s
        """.formatted(
        identifier().name(),
        source.highlight(identifier.range()),
        source.highlight(existingIdentifier.range()));
  }
}
