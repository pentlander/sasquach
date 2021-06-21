package com.pentlander.sasquach.ast;

import com.pentlander.sasquach.Range;

public interface Use {
    String name();
    String alias();

    record Module(String name, String alias, Range range) implements Use {}
    record Foreign(String qualifiedName, String alias, Range range) implements Use {
        @Override
        public String name() {
            return qualifiedName;
        }
    }
}
