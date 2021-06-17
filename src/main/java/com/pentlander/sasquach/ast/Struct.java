package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;
import java.util.stream.Collectors;

public record Struct(List<Field> fields, List<Function> functions, Type type, Range range) implements Expression {
  public Struct(List<Field> fields, List<Function> functions, Range range) {
    this(fields, functions, new StructType(
        fields.stream().collect(Collectors.toMap(Field::name, field -> field.value().type()))), range);
  }

  public String name() {
    return type().typeName();
  }

  public record Field(String name, Expression value, Range range) implements Expression {
    public Type type() {
      return value.type();
    }
  }
}
