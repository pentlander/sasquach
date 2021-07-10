package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.expression.Function;

public interface ScopeWriter {
  void addFunction(Function function);

  void addLocalIdentifier(Identifier identifier);

  void addUse(Use use);
}
