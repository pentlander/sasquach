package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Struct(String name, List<Use> useList, List<Field> fields, List<Function> functions, Type type,
                     StructKind structKind,
                     Range range) implements Expression {

  public static Struct anonStruct(List<Field> fields, List<Function> functions, Range range) {
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

  public record Field(String name, Expression value, Range range) implements Expression {
    public Type type() {
      return value.type();
    }
  }

  public enum StructKind {
    LITERAL, MODULE
  }
}
