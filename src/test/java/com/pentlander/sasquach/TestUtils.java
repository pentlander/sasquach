package com.pentlander.sasquach;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;

public final class TestUtils {
  private TestUtils() {
  }


  public static void dumpGeneratedClasses(Map<String, byte[]> generatedClasses) {
    try {
      var tempPath = Files.createTempDirectory("class_dump_");
      for (Map.Entry<String, byte[]> entry : generatedClasses.entrySet()) {
        String name = entry.getKey();
        byte[] bytecode = entry.getValue();
        Compiler.saveBytecodeToFile(tempPath, name, bytecode);
      }
      System.err.println("Dumped files to: " + tempPath);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T invokeName(Class<?> clazz, String name, Object obj, Object... args)
      throws Exception {
    for (var method : clazz.getMethods()) {
      if (method.getName().equals(name)) {
        return (T) method.invoke(obj, args);
      }
    }
    throw new NoSuchMethodException();
  }

  public static <T> T invokeName(Class<?> clazz, String name) throws Exception {
    return invokeName(clazz, name, null);
  }

  @SuppressWarnings("unchecked")
  public static <T> T invokeFirst(Class<?> clazz, Object obj, Object... args) throws Exception {
    return (T) clazz.getMethods()[0].invoke(obj, args);
  }

  public static  <T> T invokeFirst(Class<?> clazz) throws Exception {
    return invokeFirst(clazz, null);
  }
}
