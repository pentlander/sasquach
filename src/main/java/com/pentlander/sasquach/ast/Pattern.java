package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;
import java.util.List;

public sealed interface Pattern extends Node {
  Identifier id();

  record Singleton(Identifier id) implements Pattern {
    @Override
    public Range range() {
      return id.range();
    }
  }

  record VariantTuple(Identifier id, List<PatternVariable> bindings, Range range) implements Pattern {}

  record VariantStruct(Identifier id, List<PatternVariable> bindings, Range range) implements Pattern {}
}
