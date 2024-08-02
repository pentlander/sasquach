package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.ArrayType;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public record TArrayValue(ArrayType type, List<? extends TypedExpression> expressions,
                          Range range) implements TypedExpression {

  public static TArrayValue ofElementType(Type elementType, List<TypedExpression> expressions,
      Range range) {
    return new TArrayValue(new ArrayType(elementType), expressions, range);
  }
}
