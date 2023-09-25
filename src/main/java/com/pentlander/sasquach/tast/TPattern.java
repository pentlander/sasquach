package com.pentlander.sasquach.tast;

import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.ast.Id;
import com.pentlander.sasquach.type.Type;
import java.util.List;

public sealed interface TPattern extends TypedNode {
  Id id();

  record TSingleton(Id id, Type type) implements TPattern {
    @Override
    public Range range() {
      return id.range();
    }
  }

  record TVariantTuple(Id id, List<TPatternVariable> bindings, Range range) implements TPattern {
    @Override
    public Type type() {
      throw new IllegalStateException();
    }
  }

  record TVariantStruct(Id id, List<TPatternVariable> bindings, Range range) implements TPattern {
    @Override
    public Type type() {
      throw new IllegalStateException();
    }
  }
}
