package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.ArrayType;
import java.util.List;

public record TArrayValue(ArrayType type, List<? extends TypedExpression> expressions,
                          Range range) implements TypedExpression {}
