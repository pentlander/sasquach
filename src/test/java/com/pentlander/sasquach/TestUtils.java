package com.pentlander.sasquach;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class TestUtils {
  private TestUtils() {
  }


  public static void dumpGeneratedClasses(Map<String, byte[]> generatedClasses) {
    try {
      var tempPath = Files.createTempDirectory("class_dump_");
      dumpGeneratedClasses(tempPath, generatedClasses);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  public static void dumpGeneratedClasses(Path path, Map<String, byte[]> generatedClasses) {
    try {
      if (Files.isDirectory(path)) {
        try (var paths = Files.walk(path)) {
          paths.forEach(childPath -> {
            try {
              if (Files.isRegularFile(childPath)) {
                Files.delete(childPath);
              }
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
        }
      }
      for (Map.Entry<String, byte[]> entry : generatedClasses.entrySet()) {
        String name = entry.getKey();
        byte[] bytecode = entry.getValue();
        Compiler.saveBytecodeToFile(path, name, bytecode);
      }
      System.err.println("Dumped files to: file://" + path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Object staticInstance(Class<?> clazz)
      throws NoSuchFieldException, IllegalAccessException {
    return clazz.getField("INSTANCE").get(null);
  }

  @SuppressWarnings("unchecked")
  private static <T> T invokeNameInner(Class<?> clazz, String name, Object... args)
      throws Exception {
    var obj = staticInstance(clazz);
    for (var method : clazz.getMethods()) {
      if (method.getName().equals(name)) {
        return (T) method.invoke(obj, args);
      }
    }
    throw new NoSuchMethodException();
  }

  public static <T> T invokeName(Class<?> clazz, String name) throws Exception {
    return invokeNameInner(clazz, name);
  }

  public static <T> T invokeMain(Class<?> clazz) throws Exception {
    return invokeName(clazz, "main");
  }
}
