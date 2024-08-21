package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.UnqualifiedName;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.Type;

public record TVarReference(Id id, RefDeclaration refDeclaration, Type type) implements
    TypedExpression {

  public UnqualifiedName name() {
    return id.name();
  }

  @Override
  public Range range() {
    return id.range();
  }

  @Override
  public String toPrettyString() {
    return "%s: %s".formatted(id().name(), type.toPrettyString());
  }

  public sealed interface RefDeclaration {
    record Local(TLocalVariable localVariable) implements RefDeclaration {}

    record Module(QualifiedModuleId moduleId) implements RefDeclaration {}

    record Singleton(SingletonType singletonType) implements RefDeclaration {}
  }
}
