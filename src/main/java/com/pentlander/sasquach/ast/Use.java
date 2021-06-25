package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public interface Use extends Node {
    String name();
    Identifier alias();

    record Module(String name, Identifier alias, Range range) implements Use {}
    record Foreign(String qualifiedName, Identifier alias, Range range) implements Use {
        @Override
        public String name() {
            return qualifiedName;
        }
    }
}
