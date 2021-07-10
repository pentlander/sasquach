package com.pentlander.sasquach.ast.expression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;

public interface Expression extends Node {
  @JsonIgnore
  Range range();
}
