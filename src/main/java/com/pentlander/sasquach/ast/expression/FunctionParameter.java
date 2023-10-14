package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.TypeNode;
import com.pentlander.sasquach.tast.expression.TLocalVariable;
import com.pentlander.sasquach.type.Type;

/**
 * Function parameter name with a type.
 */
public record FunctionParameter(Identifier id, TypeNode typeNode) implements LocalVariable,
    TLocalVariable {
  /**
   * Name of the parameter variable.
   */
  public String name() {
    return id.name();
  }

  /**
   * Type of the parameter.
   */
  public Type type() {
    return typeNode.type();
  }

  @Override
  public Range range() {
    return id.range().join(typeNode.range());
  }

  @Override
  public String toPrettyString() {
    return name() + ": " + type().toPrettyString();
  }
}
