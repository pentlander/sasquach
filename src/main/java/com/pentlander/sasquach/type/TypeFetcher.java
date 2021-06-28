package com.pentlander.sasquach.type;

import com.pentlander.sasquach.ast.Expression;
import com.pentlander.sasquach.ast.Identifier;

public interface TypeFetcher {
    Type getType(Expression expression);

    Type getType(Identifier identifier);

    ForeignFunctionType getType(Identifier classAlias, Identifier functionName);
}
