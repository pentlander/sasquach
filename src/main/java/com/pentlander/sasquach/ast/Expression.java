package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public interface Expression {
    Type type();

    default Range range() {
        throw new IllegalStateException("Range not implemented for: " + this);
    }
}
