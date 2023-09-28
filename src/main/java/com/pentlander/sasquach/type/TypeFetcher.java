package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.expression.Expression;
import com.pentlander.sasquach.tast.TypedMember;
import com.pentlander.sasquach.type.TypeResolutionResult.TypeVariableResolver;

public interface TypeFetcher {
  Type getType(Expression expression);

  Type getType(Identifier identifier);

  ForeignFunctionType getType(Identifier classAlias, Identifier functionName);
}
