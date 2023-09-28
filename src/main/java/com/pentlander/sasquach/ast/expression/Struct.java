package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.Range.Single;
import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.TypeAlias;
import com.pentlander.sasquach.ast.Use;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RecordBuilder
public record Struct(Optional<String> name, List<Use> useList, List<TypeAlias> typeAliases,
                     List<Field> fields, List<NamedFunction> functions, StructKind structKind,
                     Range range) implements Expression {

  public Struct {
    useList = requireNonNullElse(useList, List.of());
    typeAliases = requireNonNullElse(typeAliases, List.of());
    fields = requireNonNullElse(fields, List.of());
    functions = requireNonNullElse(functions, List.of());
    requireNonNull(structKind);
    requireNonNull(range);
  }

  public static Struct literalStruct(List<Field> fields, List<NamedFunction> functions,
      Range range) {
    return new Struct(Optional.empty(),
        List.of(),
        List.of(),
        fields,
        functions,
        StructKind.LITERAL,
        range);
  }

  public static Struct variantLiteralStruct(String name, List<Field> fields,
      List<NamedFunction> functions, Range range) {
    return new Struct(Optional.of(name),
        List.of(),
        List.of(),
        fields,
        functions,
        StructKind.VARIANT,
        range);
  }

  public static StructBuilder moduleStructBuilder(String name) {
    return StructBuilder.builder().name(Optional.of(name)).structKind(StructKind.MODULE);
  }

  public static Struct moduleStruct(String name, List<Use> useList, List<TypeAlias> typeAliases,
      List<Field> fields, List<NamedFunction> functions, Range range) {
    return new Struct(Optional.of(name),
        useList,
        typeAliases,
        fields,
        functions,
        StructKind.MODULE,
        range);
  }

  private static Struct internalTupleStruct(Optional<String> name, List<Expression> expressions,
      StructKind structKind, Range range) {
    var fields = new ArrayList<Field>();
    for (int i = 0; i < expressions.size(); i++) {
      var expr = expressions.get(i);
      fields.add(new Field(new Identifier("_" + i, (Single) expr.range()), expr));
    }
    return new Struct(name, List.of(), List.of(), fields, List.of(), structKind, range);
  }

  public static Struct tupleStruct(List<Expression> expressions, Range range) {
    return internalTupleStruct(Optional.empty(), expressions, StructKind.LITERAL, range);
  }

  public static Struct variantTupleStruct(String name, List<Expression> expressions, Range range) {
    return internalTupleStruct(Optional.of(name), expressions, StructKind.VARIANT, range);
  }

  @Override
  public String toPrettyString() {
    return "{" + useList().stream()
        .map(Node::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + fields().stream()
        .map(Field::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + functions().stream()
        .map(NamedFunction::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + "}";
  }

  public record Field(Identifier id, Expression value) implements Node {
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

  public enum StructKind {
    LITERAL, MODULE, VARIANT,
  }
}
