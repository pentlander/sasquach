package com.pentlander.sasquach.name;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.Id;

record NameNotFoundError(Id id, String nodeType) implements RangedError {
  @Override
  public Range range() {
    return id.range();
  }

  @Override
  public String toPrettyString(Source source) {
    return """
        Could not find %s '%s' in scope.
        %s
        """.formatted(nodeType, id().name(), source.highlight(id.range()));
  }
}
