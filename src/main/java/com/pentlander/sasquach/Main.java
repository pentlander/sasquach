package com.pentlander.sasquach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.pentlander.sasquach.ast.CompilationUnit;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
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
            var filename = sasquachPath.getFileName().toString();
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

            var bytecode = new BytecodeGenerator().generateBytecode(compilationUnit);
//            System.out.println(
//                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(compilationUnit));
            bytecode.generatedBytecode().forEach(
                (name, byteCode) -> saveBytecodeToFile(Paths.get("out"), name, byteCode));
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    public static void saveBytecodeToFile(Path outputDir, String className, byte[] byteCode) {
        try  {
            var filepath = outputDir.resolve(className + ".class");
            Files.createDirectories(outputDir);
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

                var visitor = new Visitor.CompilationUnitVisitor();
                return parser.compilationUnit().accept(visitor);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
