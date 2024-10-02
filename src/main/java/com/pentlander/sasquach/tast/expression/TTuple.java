package com.pentlander.sasquach.tast.expression;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.Util;
import com.pentlander.sasquach.ast.expression.Tuple;
import com.pentlander.sasquach.type.StructType;
import com.pentlander.sasquach.type.Type;
import com.pentlander.sasquach.type.UniversalType;
import java.util.List;
import java.util.stream.IntStream;

public record TTuple(List<TField> fields, Range range) implements TStruct {
  @Override
  public StructType structType() {
    var fieldMap = fields.stream().collect(Util.toSeqMap(TField::name, TField::type));
    var name = Tuple.tupleName(fields.size());
    return new StructType(name,fieldMap);
  }


  public List<Type> constructorParams() {
    return IntStream.range(0, fields.size())
        .mapToObj(i -> new UniversalType("A" + i))
        .map(Type.class::cast)
        .toList();
  }
}
