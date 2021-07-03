package com.pentlander.sasquach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pentlander.sasquach.ast.CompilationUnit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import com.pentlander.sasquach.type.TypeResolver;
import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class Main {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        try {
            var sasquachPath = Paths.get(args[0]);
            var compilationUnit = new Parser().getCompilationUnit(sasquachPath);
            var source = new Source(Files.readAllLines(sasquachPath));
            var validator = new AstValidator(compilationUnit, source);
            var compileErrors = validator.validate();
            if (!compileErrors.isEmpty()) {
                for (Error compileError : compileErrors) {
                    System.out.printf("error: %s\n", compileError.toPrettyString(source));
                }
                System.exit(1);
            }

            var resolver = new TypeResolver();
            var typeErrors = resolver.resolve(compilationUnit);
            if (!typeErrors.isEmpty()) {
                for (Error typeError : typeErrors) {
                    System.out.printf("error: %s\n", typeError.toPrettyString(source));
                }
                System.exit(1);
            }

            var bytecode = new BytecodeGenerator(resolver).generateBytecode(compilationUnit);
//            System.out.println(
//                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(compilationUnit));
            var outputPath = Paths.get("out");
            Files.walkFileTree(outputPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
            // TODO: Come up with non-hacky way to copy over the runtime files
            var runtimePath = Paths.get("build/classes/java/main/com/pentlander/sasquach/runtime/");
            Files.walkFileTree(runtimePath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                    var outPath = outputPath.resolve("com/pentlander/sasquach/runtime/");
                    Files.createDirectories(outPath);
                    Files.copy(file, outPath.resolve(file.getFileName()));
                    return FileVisitResult.CONTINUE;
                }
            });
            bytecode.generatedBytecode().forEach(
                (name, byteCode) -> saveBytecodeToFile(outputPath, name, byteCode));
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public static void saveBytecodeToFile(Path outputDir, String className, byte[] byteCode) {
        try  {
            var filepath = outputDir.resolve(className.replace('.' ,'/') + ".class");
            Files.createDirectories(filepath.getParent());
            Files.write(filepath, byteCode);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static class SasquachTreeWalkErrorListener extends BaseErrorListener {
        private final List<BasicError> compileErrors = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            compileErrors.add(new BasicError(msg));
        }

        public List<BasicError> getCompileErrors() {
            return List.copyOf(compileErrors);
        }
    }

    static class Parser {
        public CompilationUnit getCompilationUnit(Path filePath) {
            try {
                CharStream charStream = new ANTLRFileStream(filePath.toAbsolutePath().toString());
                var errorListener = new SasquachTreeWalkErrorListener();
                var lexer = new SasquachLexer(charStream);
                lexer.addErrorListener(errorListener);

                var tokenStream = new CommonTokenStream(lexer);
                var parser = new SasquachParser(tokenStream);
                parser.addErrorListener(errorListener);

                var packageName = filePath.getFileName().toString().split("\\.")[0];
                var visitor = new Visitor.CompilationUnitVisitor(packageName);
                return parser.compilationUnit().accept(visitor);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
