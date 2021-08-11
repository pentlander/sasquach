package com.pentlander.sasquach.name;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.Identifier;

record DuplicateNameError(Id identifier, Id existingIdentifier) implements
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
