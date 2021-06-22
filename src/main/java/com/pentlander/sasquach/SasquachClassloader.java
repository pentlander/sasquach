package com.pentlander.sasquach;

public class SasquachClassloader extends ClassLoader {
    public Class<?> addClass(String name, byte[] bytecode) {
        return defineClass(name, bytecode, 0, bytecode.length);
    }
}
