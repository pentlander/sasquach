package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record Struct(Scope scope, Optional<String> name, List<Use> useList, List<Field> fields,
                     List<Function> functions,
                     StructKind structKind,
                     Range range) implements Expression {

  public static Struct literalStruct(Scope scope, List<Field> fields, List<Function> functions, Range range) {
    return new Struct(scope, Optional.empty(), List.of(), fields, functions, StructKind.LITERAL, range);
  }

  public static Struct moduleStruct(Scope scope, String name, List<Use> useList, List<Field> fields,
                                    List<Function> functions,
                                    Range range) {
    return new Struct(scope, Optional.of(name), useList, fields, functions, StructKind.MODULE, range);
  }

  public record Field(Identifier id, Expression value) implements Expression {
    public String name() {
      return id.name();
    }

    @Override
    public Range range() {
      return id.range().join(value.range());
    }
  }

  public enum StructKind {
    LITERAL, MODULE
  }
}
