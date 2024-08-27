package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.QualifiedModuleName;

/**
 * Classloader for Sasquach classes.
 */
public class SasquachClassloader extends ClassLoader {
  static {
    registerAsParallelCapable();
  }

  public Class<?> addClass(String name, byte[] bytecode) {
    return defineClass(name, bytecode, 0, bytecode.length);
  }

  public void linkClass(Class<?> clazz) {
    resolveClass(clazz);
  }

  public Class<?> loadModule(QualifiedModuleName moduleName) throws ClassNotFoundException {
    return loadClass(moduleName.javaName());
  }
}
