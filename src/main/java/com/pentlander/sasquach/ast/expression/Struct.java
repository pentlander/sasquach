package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.Util;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.UnqualifiedName;
import com.pentlander.sasquach.name.UnqualifiedTypeName;
import java.util.List;
import java.util.stream.Collectors;

public sealed interface Struct extends Expression permits LiteralStruct,
    StructWithName {
  static Struct literalStruct(List<Field> fields, List<NamedFunction> functions, List<VarReference> spreads, Range range) {
    return new LiteralStruct(fields, functions, spreads, range);
  }

  static Struct tupleStruct(List<Expression> expressions, Range range) {
    return new LiteralStruct(tupleFields(expressions), List.of(), List.of(), range);
  }

  static Struct namedStructConstructor(UnqualifiedTypeName name, List<Field> fields, Range range) {
    return new NamedStruct(name, fields, range);
  }

  static ModuleStructBuilder moduleStructBuilder(QualifiedModuleName name) {
    return ModuleStructBuilder.builder().moduleName(name);
  }

  private static List<Field> tupleFields(List<Expression> expressions) {
    return Util.tupleFields(
        expressions,
        (name, expr) -> new Field(new Id(name, (Single) expr.range()), expr));
  }

  @Override
  default String toPrettyString() {
    return "{ " + fields().stream()
        .map(Field::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + functions().stream()
        .map(NamedFunction::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + "}";
  }

  List<Field> fields();

  List<NamedFunction> functions();

  record Field(Id id, Expression value) implements Node {
    public UnqualifiedName name() {
      return id.name();
    }

    @Override
    public Range range() {
      return id.range().join(value.range());
    }

    @Override
    public String toPrettyString() {
      return id().name() + " = " + value().toPrettyString();
    }
  }
}
