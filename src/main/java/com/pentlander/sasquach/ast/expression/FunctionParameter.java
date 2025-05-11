package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.typenode.TypeNode;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.Type;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Function parameter captureName with a type.
 */
public record FunctionParameter(Id id, @Nullable Id label, @Nullable TypeNode typeNode,
                                @Nullable Expression defaultExpr) implements LocalVariable {
  /**
   * Name of the parameter variable.
   */
  public UnqualifiedName name() {
    return id.name();
  }

  /**
   * Type of the parameter.
   */
  public Optional<Type> type() {
    return Optional.ofNullable(typeNode).map(TypeNode::type);
  }

  @Override
  public Range range() {
    return typeNode != null ? id.range().join(typeNode.range()) : id.range();
  }

  @Override
  public String toPrettyString() {
    var builder = new StringBuilder(name().toString());
    if (typeNode != null) {
      builder.append(": ").append(typeNode.type().toPrettyString());
    }
    return builder.toString();
  }
}
