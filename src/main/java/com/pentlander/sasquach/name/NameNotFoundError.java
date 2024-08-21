package com.pentlander.sasquach.name;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.Identifier;
import java.util.Arrays;
import java.util.List;

record NameNotFoundError(Identifier id, String nodeType, List<String> suggestions) implements RangedError {
  public NameNotFoundError(Identifier id, String nodeType) {
    this(id, nodeType, List.of());
  }

  @Override
  public Range range() {
    return id.range();
  }

  @Override
  public String toPrettyString(Source source) {
    var matchesStr = "";
    if (!suggestions.isEmpty()) {
      matchesStr = "\nPossible matches: \n  " + String.join("\n  ", suggestions);
    }
    return """
        Could not find %s '%s' in scope.
        %s%s
        """.formatted(nodeType, id().name(), source.highlight(id.range()), matchesStr);
  }
}
