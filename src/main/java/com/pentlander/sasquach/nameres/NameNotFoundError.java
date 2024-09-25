package com.pentlander.sasquach.nameres;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.RangedError;
import com.pentlander.sasquach.Source;
import com.pentlander.sasquach.ast.id.Identifier;
import com.pentlander.sasquach.name.Name;
import java.util.List;

record NameNotFoundError(Name name, Range range, String nodeType, List<String> suggestions) implements RangedError {
  public NameNotFoundError(Identifier id, String nodeType, List<String> suggestions) {
    this(id.name(), id.range(), nodeType, suggestions);
  }
  public NameNotFoundError(Identifier id, String nodeType) {
    this(id, nodeType, List.of());
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
        """.formatted(nodeType, name(), source.highlight(range()), matchesStr);
  }
}
