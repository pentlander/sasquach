package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.Objects;

public final class FieldAccess implements Expression {
  private final Expression expr;
  private final String fieldName;
  private final Range range;

  public FieldAccess(Expression expr, String fieldName, Range range) {
    this.expr = expr;
    this.fieldName = fieldName;
    this.range = range;
  }

  @Override
  public Type type() {
    var t = (StructType)expr.type();
    return t.fieldTypes().get(fieldName);
  }

  public Expression expr() {
    return expr;
  }

  public String fieldName() {
    return fieldName;
  }

  public Range range() {
    return range;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    return obj instanceof FieldAccess o && Objects.equals(expr, o.expr) &&
        Objects.equals(fieldName, o.fieldName) &&
        Objects.equals(range, o.range);
  }

  @Override
  public int hashCode() {
    return Objects.hash(expr, fieldName, range);
  }

  @Override
  public String toString() {
    return "FieldAccess[" +
        "expr=" + expr + ", " +
        "fieldName=" + fieldName + ", " +
        "range=" + range + ']';
  }

}
