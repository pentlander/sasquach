package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;

public record TThisExpr(StructType type, Range range) implements TypedExpression {}
