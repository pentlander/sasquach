package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.tast.expression.TFunction;
import com.pentlander.sasquach.tast.expression.TypedExpression;
import com.pentlander.sasquach.type.FunctionType;
import java.util.List;

public record TNamedFunction(Id id, TFunction function) implements TypedNode, TypedMember {
  public UnqualifiedName name() {
    return id().name();
  }

  public List<TFunctionParameter> parameters() {
    return function.parameters();
  }

  public TypedExpression expression() {
    return function.expression();
  }

  @Override
  public FunctionType type() {
    return function.type();
  }

  @Override
  public Range range() {
    return id.range().join(function.range());
  }

  public String toPrettyString() {
    return "%s = %s".formatted(id().name(), function.toPrettyString());
  }
}
