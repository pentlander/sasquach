package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.tast.expression.TLocalVariable;
import com.pentlander.sasquach.type.Type;

/**
 * Function parameter captureName with a type.
 */
public record TFunctionParameter(Id id, Type type, Range range) implements TLocalVariable {
  /**
   * Name of the parameter variable.
   */
  public String name() {
    return id.name();
  }

  @Override
  public String toPrettyString() {
    return name() + ": " + type().toPrettyString();
  }
}
