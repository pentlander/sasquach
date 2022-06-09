package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;

public record HiddenVariable(Node parentNode) implements LocalVariable {

  @Override
  public Identifier id() {
    // Temp hack until var index calculation is moved to bytecode gen
    var range = switch (parentNode.range()) {
      case Range.Single r -> r;
      case Range.Multi r -> new Range.Single(r.sourcePath(), r.start(), 1);
    };
    return new Identifier("hidden_" + parentNode.getClass().getSimpleName(), range);
  }

  @Override
  public Range range() {
    return parentNode.range();
  }
}
