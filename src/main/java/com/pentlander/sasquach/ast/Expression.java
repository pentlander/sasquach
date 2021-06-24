package com.pentlander.sasquach.ast;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pentlander.sasquach.Range;
import com.pentlander.sasquach.type.Type;

public interface Expression extends Node {
    Type type();

    @JsonIgnore
    Range range();
}
