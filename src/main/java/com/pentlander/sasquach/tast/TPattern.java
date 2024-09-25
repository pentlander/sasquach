package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.id.Identifier;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import java.util.List;

public sealed interface TPattern extends TypedNode {
  Identifier id();

  record TSingleton(Identifier id, SingletonType type) implements TPattern {
    @Override
    public Range range() {
      return id.range();
    }
  }

  record TVariantTuple(Identifier id, StructType type, List<TPatternVariable> bindings,
                       Range range) implements TPattern {}

  record TVariantStruct(Identifier id, StructType type, List<TPatternVariable> bindings,
                        Range range) implements TPattern {}
}
