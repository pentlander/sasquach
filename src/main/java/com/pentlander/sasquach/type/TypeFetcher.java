package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;

public interface TypeFetcher {
  Type getType(Expression expression);

  Type getType(Identifier identifier);

  ForeignFunctionType getType(Identifier classAlias, Identifier functionName);
}
