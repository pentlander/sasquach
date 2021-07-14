package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.VarReference;
import java.util.Optional;
import java.util.OptionalInt;

public interface ScopeReader {
  Optional<Identifier> getLocalIdentifier(VarReference varReference);

  Optional<Use> findUse(String useName);

  OptionalInt findLocalIdentifierIdx(String identifierName);

  Function findFunction(String functionName);

  String getClassName();
}
