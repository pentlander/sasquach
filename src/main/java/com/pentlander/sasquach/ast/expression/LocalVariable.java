package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;

public interface LocalVariable extends Node {
  Identifier id();
}
