package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.BuiltinType;

public record Value(BuiltinType type, String value, Range range) implements Expression,
    TypedExpression {
  @Override
  public String toPrettyString() {
    return switch (type()) {
      case BOOLEAN, INT, BYTE, SHORT, LONG, FLOAT, DOUBLE -> value;
      case CHAR -> "'" + value() + "'";
      case STRING -> "\"" + value() + "\"";
      case VOID -> "void";
    };
  }
}
