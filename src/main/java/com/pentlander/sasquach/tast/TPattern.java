package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.type.SingletonType;
import com.pentlander.sasquach.type.StructType;
import java.util.List;

public sealed interface TPattern extends TypedNode {
  Id id();

  record TSingleton(Id id, SingletonType type) implements TPattern {
    @Override
    public Range range() {
      return id.range();
    }
  }

  record TVariantTuple(Id id, StructType type, List<TPatternVariable> bindings,
                       Range range) implements TPattern {}

  record TVariantStruct(Id id, StructType type, List<TPatternVariable> bindings,
                        Range range) implements TPattern {}
}
