package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

/** A node in the abstract syntax tree. */
public interface Node {
    /** Range in the source code that this node can be found. */
    Range range();
}
