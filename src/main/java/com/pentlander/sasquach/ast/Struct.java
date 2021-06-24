package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Struct(String name, List<Use> useList, List<Field> fields, List<Function> functions, Type type,
                     StructKind structKind,
                     Range range) implements Expression {

  public static Struct literalStruct(List<Field> fields, List<Function> functions, Range range) {
    var type = new StructType(typeFromFields(fields));
    return new Struct(type.typeName(), List.of(), fields, functions, type, StructKind.LITERAL, range);
  }

  public static Struct moduleStruct(String name, List<Use> useList, List<Field> fields, List<Function> functions,
                                    Range range) {
    var type = new StructType(name, typeFromFields(fields));
    return new Struct(name, useList, fields, functions, type, StructKind.MODULE, range);
  }

  private static Map<String, Type> typeFromFields(List<Field> fields) {
    return fields.stream().collect(Collectors.toMap(Field::name, Field::type));
  }

  public String name() {
    return type().typeName();
  }

  public record Field(Identifier id, Expression value) implements Expression {
    public String name() {
      return id.name();
    }

    public Type type() {
      return value.type();
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
