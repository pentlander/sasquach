package com.pentlander.sasquach.ast;

import java.util.Optional;
import java.util.OptionalInt;

public interface ScopeReader {
  Optional<Identifier> getLocalIdentifier(String identifierName);

  Optional<Use> findUse(String useName);

  OptionalInt findLocalIdentifierIdx(String identifierName);

  Function findFunction(String functionName);

  String getClassName();
}
