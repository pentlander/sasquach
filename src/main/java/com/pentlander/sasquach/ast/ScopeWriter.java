package com.pentlander.sasquach.ast;

public interface ScopeWriter {
  void addFunction(Function function) ;

  void addLocalIdentifier(Identifier identifier) ;

  void addUse(Use use);
}
