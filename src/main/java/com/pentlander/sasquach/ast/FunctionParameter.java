package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.type.Type;

/**
 * Function parameter name with a type.
 */
public record FunctionParameter(Identifier id, TypeNode typeNode) implements Node {
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

  /**
   * Convenience method to convert a parameter to a reference.
   */
  public VarReference toReference() {
    return new VarReference(id);
  }
}
