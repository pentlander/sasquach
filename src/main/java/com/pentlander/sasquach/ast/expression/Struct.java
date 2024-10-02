package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.id.Id;
import com.pentlander.sasquach.name.QualifiedModuleName;
import com.pentlander.sasquach.name.QualifiedTypeName;
import com.pentlander.sasquach.name.UnqualifiedName;
import java.util.List;
import java.util.stream.Collectors;

public sealed interface Struct extends Expression permits LiteralStruct, StructWithName, Tuple {

  static Struct literalStruct(List<Field> fields, List<NamedFunction> functions, List<VarReference> spreads, Range range) {
    return new LiteralStruct(fields, functions, spreads, range);
  }

  static Struct tupleStruct(List<Expression> expressions, Range range) {
    return Tuple.of(expressions, range);
  }

  static Struct namedStructConstructor(QualifiedTypeName name, List<Field> fields, Range range) {
    return new NamedStruct(name, fields, range);
  }

  static ModuleStructBuilder moduleStructBuilder(QualifiedModuleName name) {
    return ModuleStructBuilder.builder().moduleName(name);
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
