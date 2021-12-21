package com.pentlander.sasquach;

import static java.util.Objects.requireNonNull;

import com.pentlander.sasquach.ast.CompilationUnit;
import com.pentlander.sasquach.backend.BytecodeGenerator;
import com.pentlander.sasquach.backend.BytecodeResult;
import com.pentlander.sasquach.name.ModuleResolver;
import com.pentlander.sasquach.type.TypeResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.atteo.classindex.ClassIndex;

public class Compiler {
  private final CompilationUnitParser parser = new CompilationUnitParser();

  Sources findFiles(Path sourcePath) throws IOException {
    if (Files.isDirectory(sourcePath)) {
      var sources = new HashMap<SourcePath, Source>();
      Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path filePath, BasicFileAttributes attrs) throws IOException {
          if (Files.isDirectory(filePath)) {
            return FileVisitResult.CONTINUE;
          }

          var relPath = sourcePath.relativize(filePath);
          var packageName = Stream.of(
                  relPath.getParent() != null ? relPath.getParent().toString() : "",
                  strippedFileName(relPath)).filter(s -> !s.isBlank())
              .collect(Collectors.joining("/"));
          var source = new Source(SourcePath.fromPath(filePath), packageName,
              Files.readAllLines(filePath));
          sources.put(source.path(), source);
          return FileVisitResult.CONTINUE;
        }
      });
      return Sources.fromMap(sources);
    } else {
      var source = new Source(SourcePath.fromPath(sourcePath),
          strippedFileName(sourcePath),
          Files.readAllLines(sourcePath));
      return Sources.single(source);
    }
  }

  public static String strippedFileName(Path path) {
    return path.getFileName().toString().split("\\.")[0];
  }

  public BytecodeResult compile(Source source) throws CompilationException {
    return new BytecodeResult(compileSources(Sources.single(source)));
  }

  private Map<String, byte[]> compileSources(Sources sources) throws CompilationException {
    var compUnits = new ArrayList<CompilationUnit>();
    for (Source source : sources.values()) {
      var compilationUnit = parser.getCompilationUnit(source);
      compUnits.add(compilationUnit);

      var validator = new AstValidator(compilationUnit);
      var compileErrors = validator.validate();
      if (!compileErrors.isEmpty()) throw new CompilationException(source, compileErrors);
    }

    var nameResolver = new ModuleResolver();
    var nameResolutionResult = nameResolver.resolveCompilationUnits(compUnits);
    nameResolutionResult.errors().throwIfNotEmpty(sources);

    var typeResolver = new TypeResolver(nameResolutionResult);
    for (var compUnit : compUnits) {
      typeResolver.resolve(compUnit).throwIfNotEmpty(sources);
    }

    var bytecodeResults = new HashMap<String, byte[]>();
    var bytecodeGenerator = new BytecodeGenerator(nameResolver, typeResolver);
    for (var compUnit : compUnits) {
      bytecodeResults.putAll(bytecodeGenerator.generateBytecode(compUnit).generatedBytecode());
    }
    return bytecodeResults;
  }

  public void compile(Path sourcePath, Path outputPath) {
    try {
      var sources = findFiles(sourcePath);
      Map<String, byte[]> bytecodeResults;
      try {
        bytecodeResults = compileSources(sources);
      } catch (CompilationException e) {
        printErrors(sources, e.errors());
        return;
      }

      clearDestDir(outputPath);
      writeRuntimeFiles(outputPath);
      for (var entry : bytecodeResults.entrySet()) {
        String name = entry.getKey();
        byte[] byteCode = entry.getValue();
        saveBytecodeToFile(outputPath, name, byteCode);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void printErrors(Sources sources, List<? extends Error> errors) {
    if (!errors.isEmpty()) {
      for (Error compileError : errors) {
        System.out.printf("error: %s\n", compileError.toPrettyString(sources));
      }
    }
  }

  public static void saveBytecodeToFile(Path outputDir, String className, byte[] byteCode) throws IOException {
      var filepath = outputDir.resolve(className.replace('.', '/') + ".class");
      Files.createDirectories(filepath.getParent());
      Files.write(filepath, byteCode);
  }

  void clearDestDir(Path outputPath) throws IOException {
    Files.walkFileTree(outputPath, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  void writeRuntimeFiles(Path outputPath) throws IOException {
    var outPath = outputPath.resolve("com/pentlander/sasquach/runtime/");
    Files.createDirectories(outPath);
    for (var clazz : ClassIndex.getPackageClasses("com.pentlander.sasquach.runtime")) {
      var resourcePath = Paths.get(clazz.getName().replace('.', '/') + ".class");
      var classFileBytes = requireNonNull(Main.class.getClassLoader()
          .getResourceAsStream(resourcePath.toString())).readAllBytes();
      Files.write(outPath.resolve(resourcePath.getFileName()), classFileBytes);
    }
  }
}
