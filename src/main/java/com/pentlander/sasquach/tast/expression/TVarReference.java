package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.Type;

public record TVarReference(UnqualifiedName name, RefDeclaration refDeclaration, Type type, Range range) implements
    TypedExpression {

  @Override
  public String toPrettyString() {
    return "%s: %s".formatted(name(), type.toPrettyString());
  }

  public sealed interface RefDeclaration {
    record Local(TLocalVariable localVariable) implements RefDeclaration {}

    record Module(QualifiedModuleName moduleName) implements RefDeclaration {}

    record Singleton(SingletonType singletonType) implements RefDeclaration {}
  }
}
