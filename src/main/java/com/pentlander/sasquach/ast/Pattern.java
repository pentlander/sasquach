package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;

public sealed interface Pattern extends Node {
  Id id();

  record Singleton(Id id) implements Pattern {
    @Override
    public Range range() {
      return id.range();
    }
  }

  record VariantTuple(Id id, List<PatternVariable> bindings, Range range) implements Pattern {}

  record VariantStruct(Id id, List<PatternVariable> bindings, Range range) implements Pattern {}
}
