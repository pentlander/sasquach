package com.pentlander.sasquach.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pentlander.sasquach.Range;

public interface Expression extends Node {
    Type type();

    @JsonIgnore
    Range range();
}
