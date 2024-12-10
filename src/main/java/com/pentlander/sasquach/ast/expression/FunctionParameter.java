package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.tast.expression.TLocalVariable;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.TypeVariable;
import org.jspecify.annotations.Nullable;

/**
 * Function parameter captureName with a type.
 */
public record FunctionParameter(Id id, @Nullable Id label, @Nullable TypeNode typeNode, @Nullable Expression defaultExpr) implements LocalVariable,
    TLocalVariable {
  /**
   * Name of the parameter variable.
   */
  public UnqualifiedName name() {
    return id.name();
  }

  // TODO this should really return an optional type instead of shimming a type variable
  /**
   * Type of the parameter.
   */
  public Type type() {
    return typeNode != null ? typeNode.type() : new TypeVariable(id.name().toString(), 0);
  }

  @Override
  public Range range() {
    return typeNode != null ? id.range().join(typeNode.range()) : id.range();
  }

  @Override
  public String toPrettyString() {
    return name() + ": " + type().toPrettyString();
  }
}
