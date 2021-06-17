package com.pentlander.sasquach.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pentlander.sasquach.Range;

public interface Expression {
    Type type();

    @JsonIgnore
    default Range range() {
        throw new IllegalStateException("Range not implemented for: " + this);
    }
}
