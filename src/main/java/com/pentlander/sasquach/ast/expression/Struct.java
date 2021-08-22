package com.pentlander.sasquach.ast.expression;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import com.pentlander.sasquach.Range;

import com.pentlander.sasquach.ast.Identifier;
import com.pentlander.sasquach.ast.Node;
import com.pentlander.sasquach.ast.Use;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RecordBuilder
public record Struct(Optional<String> name, List<Use> useList, List<Field> fields,
                     List<Function> functions, StructKind structKind, Range range) implements
    Expression {

  public Struct {
    useList = requireNonNullElse(useList, List.of());
    fields = requireNonNullElse(fields, List.of());
    functions = requireNonNullElse(functions, List.of());
    requireNonNull(structKind);
    requireNonNull(range);
  }

  public static Struct literalStruct(List<Field> fields, List<Function> functions, Range range) {
    return new Struct(
        Optional.empty(),
        List.of(),
        fields,
        functions,
        StructKind.LITERAL,
        range);
  }

  public static StructBuilder moduleStructBuilder(String name) {
    return StructBuilder.builder()
        .name(Optional.of(name))
        .structKind(StructKind.MODULE);
  }

  public static Struct moduleStruct(String name, List<Use> useList, List<Field> fields,
      List<Function> functions, Range range) {
    return new Struct(
        Optional.of(name),
        useList,
        fields,
        functions,
        StructKind.MODULE,
        range);
  }

  @Override
  public String toPrettyString() {
    return "{" + useList().stream().map(Node::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + fields().stream()
        .map(Field::toPrettyString).collect(Collectors.joining(", ", "", " "))
        + functions().stream().map(Function::toPrettyString)
        .collect(Collectors.joining(", ", "", " ")) + "}";
  }

  public record Field(Identifier id, Expression value) implements Expression {
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
    LITERAL, MODULE
  }
}
