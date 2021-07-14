package com.pentlander.sasquach.name;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.Identifier;

record NameNotFoundError(Identifier identifier, String nodeType) implements RangedError {
  @Override
  public Range range() {
    return identifier.range();
  }

  @Override
  public String toPrettyString(Source source) {
    return """
        Could not find %s '%s' in scope.
        %s
        """.formatted(nodeType, identifier().name(), source.highlight(identifier.range()));
  }
}
