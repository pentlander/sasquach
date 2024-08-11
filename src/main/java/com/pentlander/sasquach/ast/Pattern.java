package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;

public sealed interface Pattern extends Node {
  TypeId id();

  record Singleton(TypeId id) implements Pattern {
    @Override
    public Range range() {
      return id.range();
    }
  }

  record VariantTuple(TypeId id, List<PatternVariable> bindings, Range range) implements Pattern {}

  record VariantStruct(TypeId id, List<PatternVariable> bindings, Range range) implements Pattern {}
}
