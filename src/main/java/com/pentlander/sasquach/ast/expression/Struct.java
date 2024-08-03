package com.pentlander.sasquach.ast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.Use;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public sealed interface Struct extends Expression permits LiteralStruct,
    StructWithName {
  static Struct literalStruct(List<Field> fields, List<NamedFunction> functions, List<VarReference> spreads, Range range) {
    return new LiteralStruct(fields, functions, spreads, range);
  }

  static Struct variantLiteralStruct(String name, List<Field> fields, List<NamedFunction> functions,
      Range range) {
    return new NamedStruct(name, fields, functions, range);
  }

  static ModuleStructBuilder moduleStructBuilder(String name) {
    return ModuleStructBuilder.builder().name(name);
  }

  static Struct moduleStruct(String name, List<Use> useList, List<TypeAlias> typeAliases,
      List<Field> fields, List<NamedFunction> functions, Range range) {
    return new ModuleStruct(name,
        useList,
        typeAliases,
        fields,
        functions,
        range);
  }

  private static List<Field> tupleFields(List<Expression> expressions) {
    var fields = new ArrayList<Field>();
    for (int i = 0; i < expressions.size(); i++) {
      var expr = expressions.get(i);
      fields.add(new Field(new Id("_" + i, (Single) expr.range()), expr));
    }
    return fields;
  }

  static Struct tupleStruct(List<Expression> expressions, Range range) {
    return new LiteralStruct(tupleFields(expressions), List.of(), List.of(), range);
  }

  static NamedStruct variantTupleStruct(String name, List<Expression> expressions, Range range) {
    return new NamedStruct(name, tupleFields(expressions), List.of(), range);
  }

  @Override
  default String toPrettyString() {
    return "{" + fields().stream()
        .map(Field::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + functions().stream()
        .map(NamedFunction::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + "}";
  }

  List<Field> fields();

  List<NamedFunction> functions();

  StructKind structKind();

  enum StructKind {
    LITERAL, MODULE, NAMED,
  }

  record Field(Id id, Expression value) implements Node {
    public String name() {
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
