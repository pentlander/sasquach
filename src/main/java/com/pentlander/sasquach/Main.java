package com.pentlander.sasquach;

import com.pentlander.sasquach.ast.*;
import org.antlr.v4.runtime.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        try {
            var sasquachPath = Paths.get(args[0]);
            var filename = sasquachPath.getFileName().toString();
            var compilationUnit = new Parser().getCompilationUnit(sasquachPath);
            System.out.println(compilationUnit);
            var bytecode = new BytecodeGenerator().generateBytecode(compilationUnit);
            saveBytecodeToFile(filename, bytecode);
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
        }
    }

    private static void saveBytecodeToFile(String filename, byte[] byteCode) {
        var classfileName = filename.replace(".sasq", ".class");
        try (var outputStream = new FileOutputStream(classfileName)) {
            outputStream.write(byteCode);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static class ClassGenerator {
        private static final int CLASS_VERSION = Opcodes.V16;
        private final ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS);

        public ClassWriter generate(ModuleDeclaration moduleDeclaration) {
            String name = moduleDeclaration.name();
            classWriter.visit(CLASS_VERSION, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
            for (Function func : moduleDeclaration.functions()) {
                generateFunction(classWriter, func);
            }
            classWriter.visitEnd();
            return classWriter;
        }

        private void generateFunction(ClassWriter classWriter, Function function) {
            String descriptor = DescriptorFactory.getMethodDescriptor(function);
            MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, function.name(), descriptor, null, null);
            methodVisitor.visitCode();
            var exprGenerator = new ExpressionGenerator(methodVisitor);
            function.expressions().forEach(expr -> exprGenerator.generate(expr, function.scope()));
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(-1, -1);
            methodVisitor.visitEnd();
        }
    }

    static class ExpressionGenerator {
        private final MethodVisitor methodVisitor;

        ExpressionGenerator(MethodVisitor methodVisitor) {
            this.methodVisitor = methodVisitor;
        }

        public void generate(Expression expression, Scope scope) {
            if (expression instanceof PrintStatement p) {
                methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generate(p.expression(), scope);
                String descriptor = "(%s)V".formatted(p.expression().type().descriptor());
                StructType owner = new StructType("java.io.PrintStream");
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner.internalName(), "println", descriptor, false);
            } else if (expression instanceof  VariableDeclarations varDecl) {
                var varDeclExpr = varDecl.expression();
                int idx = scope.findIdentifierIdx(varDecl.name());
                if (varDeclExpr instanceof Value value) {
                    var type = value.type();
                    if (type == BuiltinType.INT) {
                        int intValue = Integer.parseInt(value.value());
                        methodVisitor.visitIntInsn(Opcodes.BIPUSH, intValue);
                        methodVisitor.visitVarInsn(Opcodes.ISTORE, idx);
                    } else if (type == BuiltinType.STRING) {
                        methodVisitor.visitLdcInsn(value);
                        methodVisitor.visitVarInsn(Opcodes.ASTORE, idx);
                    }
                }
            } else if (expression instanceof Identifier id) {
                int idx = scope.findIdentifierIdx(id.name());
                if (idx != -1 && id.type() == BuiltinType.INT) {
                    System.out.println(scope.getIdentifiers());
                    System.out.printf("Var '%s', index: %d\n", id.name(), idx);
                    methodVisitor.visitVarInsn(Opcodes.ILOAD, idx);
                }
            } else if (expression instanceof Value value) {
                var type = value.type();
                if (type == BuiltinType.INT) {
                    int intValue = Integer.parseInt(value.value());
                    methodVisitor.visitIntInsn(Opcodes.BIPUSH, intValue);
                } else if (type == BuiltinType.STRING) {
                    methodVisitor.visitLdcInsn(value.value());
                }
            } else if (expression instanceof FunctionParameter funcParam) {
                if (funcParam.type() == BuiltinType.INT) {
                    methodVisitor.visitVarInsn(Opcodes.ILOAD, funcParam.index());
                }
            } else if (expression instanceof FunctionCall funcCall) {
                funcCall.arguments().forEach(arg -> generate(arg, scope));
                String descriptor = DescriptorFactory.getMethodDescriptor(funcCall.signature());
                Type owner = Objects.requireNonNullElseGet(funcCall.owner(), () -> new StructType(scope.getClassName()));
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner.internalName(), funcCall.functionName(), descriptor, false);
            }
        }
    }

    static class DescriptorFactory {
        private static final Map<BuiltinType, String> FIELD_DESCRIPTOR_MAPPING =
                Map.of(BuiltinType.VOID, "V", BuiltinType.INT, "I");

        public static String getMethodDescriptor(Function function) {
            return getMethodDescriptor(function.arguments(), function.type());
        }

        public static String getMethodDescriptor(FunctionSignature signature) {
            return getMethodDescriptor(signature.parameters(), signature.returnType());
        }

        public static String getMethodDescriptor(Collection<FunctionParameter> parameters, Type returnType) {
            String paramDescriptor = parameters.stream()
                    .map(param -> param.type().descriptor())
                    .collect(Collectors.joining("", "(", ")"));
            return paramDescriptor + returnType.descriptor();
        }
    }

    static class BytecodeGenerator implements Opcodes {
        public byte[] generateBytecode(CompilationUnit compilationUnit) throws Exception {
            ModuleDeclaration moduleDeclaration = compilationUnit.module();
            return new ClassGenerator().generate(moduleDeclaration).toByteArray();
        }
    }

    static class SasquachTreeWalkErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            System.out.printf("You done goofed at line %d, char %d. Details:\n%s\n", line, charPositionInLine, msg);
        }
    }

    static class Parser {
        public CompilationUnit getCompilationUnit(Path filePath) {
            try {
                CharStream charStream = new ANTLRFileStream(filePath.toAbsolutePath().toString());
                var lexer = new SasquachLexer(charStream);
                var tokenStream = new CommonTokenStream(lexer);
                var parser = new SasquachParser(tokenStream);
                var errorListener = new SasquachTreeWalkErrorListener();
                parser.addErrorListener(errorListener);

                var visitor = new CompilationUnitVisitor();
                return parser.compilationUnit().accept(visitor);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    static class CompilationUnitVisitor extends SasquachBaseVisitor<CompilationUnit> {
        @Override
        public CompilationUnit visitCompilationUnit(SasquachParser.CompilationUnitContext ctx) {
            String moduleName = ctx.moduleDeclaration().moduleName().getText();
            ModuleVisitor moduleVisitor = new ModuleVisitor();
            ModuleDeclaration module = ctx.moduleDeclaration().accept(moduleVisitor);
            return new CompilationUnit(module);
        }
    }

    static class ModuleVisitor extends SasquachBaseVisitor<ModuleDeclaration> {
        private Scope scope;

        @Override
        public ModuleDeclaration visitModuleDeclaration(SasquachParser.ModuleDeclarationContext ctx) {
            String name = ctx.moduleName().getText();
            var functionSignatureVisitor = new FunctionSignatureVisitor();
            var functionsCtx = ctx.moduleBody().function();
            var metadata = new Metadata(ctx.moduleName().getText());
            scope = new Scope(metadata);
            functionsCtx.stream()
                    .map(method -> method.functionDeclaration().accept(functionSignatureVisitor))
                    .forEach(scope::addSignature);
            var functions = functionsCtx.stream()
                    .map(method -> method.accept(new FunctionVisitor(new Scope(metadata, scope))))
                    .toList();
            return new ModuleDeclaration(name, functions);
        }
    }

    static class FunctionVisitor extends SasquachBaseVisitor<Function> {
        private final Scope scope;

        FunctionVisitor(Scope scope) {
            this.scope = scope;
        }

        @Override
        public Function visitFunction(SasquachParser.FunctionContext ctx) {
            String name = ctx.functionDeclaration().functionName().getText();

            SasquachParser.TypeContext typeCtx = ctx.functionDeclaration().type();
            var typeVisitor = new TypeVisitor();
            Type returnType = typeCtx.accept(typeVisitor);

            List<FunctionParameter> parameters = ctx.functionDeclaration().functionArgument().stream()
                    .map(paramCtx -> new FunctionParameter(paramCtx.ID().getText(), paramCtx.type().accept(new TypeVisitor()), paramCtx.index))
                    .peek(param -> scope.addIdentifier(new Identifier(param.name(), param)))
                    .toList();

            List<Expression> expressions = ctx.blockStatement().stream()
                    .map(blockCtx -> blockCtx.accept(new ExpressionVisitor(scope)))
                    .toList();

            return new Function(scope, name, returnType, parameters, expressions);
        }
    }

    static class ExpressionVisitor extends SasquachBaseVisitor<Expression> {
        private final Scope scope;

        ExpressionVisitor(Scope scope) {
            this.scope = scope;
        }

        @Override
        public Expression visitIdentifier(SasquachParser.IdentifierContext ctx) {
            return scope.findIdentifier(ctx.getText());
        }

        @Override
        public Expression visitValue(SasquachParser.ValueContext ctx) {
            String value = ctx.getText();
            var visitor = new TypeVisitor();
            Type type = ctx.accept(visitor);
            return new Value(type, value);
        }

        @Override
        public Expression visitFunctionCall(SasquachParser.FunctionCallContext ctx) {
            String funName = ctx.functionName().getText();
            FunctionSignature signature = scope.findFunctionSignature(funName);
            List<FunctionParameter> funcParams = signature.parameters();
            List<SasquachParser.ExpressionContext> argExpressions = ctx.expressionList().expression();
            if (funcParams.size() != argExpressions.size()) {
                throw new IllegalStateException("Wrong number of args");
            }

            var arguments = new ArrayList<Expression>();
            for (int i = 0; i < funcParams.size(); i++) {
                var formalParam = funcParams.get(i);
                var argExpressionCtx = argExpressions.get(i);
                var visitor = new ExpressionVisitor(scope);
                Expression argument = argExpressionCtx.accept(visitor);
                if (!Objects.equals(argument.type(), formalParam.type())) {
                    throw new IllegalStateException("Arg type mismatch, arg '%s' has type '%s' but param is of type '%s'".formatted(argument.toString(), argument.type(), formalParam.type()));
                }
                arguments.add(argument);
            }

            return new FunctionCall(signature, arguments, null);
        }

        @Override
        public Expression visitPrintStatement(SasquachParser.PrintStatementContext ctx) {
            Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
            return new PrintStatement(expr);
        }

        @Override
        public Expression visitVariableDeclaration(SasquachParser.VariableDeclarationContext ctx) {
            var idName = ctx.identifier().getText();
            Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
            var identifier = new Identifier(idName, expr);
            scope.addIdentifier(identifier);
            return new VariableDeclarations(identifier.name(), identifier.expression(), ctx.index);
        }
    }

    static class TypeVisitor extends SasquachBaseVisitor<Type> {
        @Override
        public Type visitValue(SasquachParser.ValueContext ctx) {
            if (ctx == null) {
                return BuiltinType.VOID;
            }
            String typeString = ctx.getText();
            if (isNumeric(typeString)) {
                return BuiltinType.INT;
            }
            return new StructType(typeString);
        }

        private static boolean isNumeric(String value) {
            try {
                Integer.parseInt(value);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public Type visitPrimitiveType(SasquachParser.PrimitiveTypeContext ctx) {
            return BuiltinType.fromString(ctx.getText());
        }

        @Override
        public Type visitStructType(SasquachParser.StructTypeContext ctx) {
            return new StructType(ctx.getText());
        }
    }

    static class FunctionSignatureVisitor extends SasquachBaseVisitor<FunctionSignature> {

        @Override
        public FunctionSignature visitFunctionDeclaration(SasquachParser.FunctionDeclarationContext ctx) {
            var typeVisitor = new TypeVisitor();
            String funcName = ctx.functionName().getText();

            List<SasquachParser.FunctionArgumentContext> paramsCtx = ctx.functionArgument();
            var params = new ArrayList<FunctionParameter>();
            for (int i = 0; i < paramsCtx.size(); i++) {
                var paramCtx = paramsCtx.get(i);
                var param = new FunctionParameter(paramCtx.ID().getText(), paramCtx.type().accept(typeVisitor), i);
                params.add(param);
            }

            return new FunctionSignature(funcName, params, ctx.type().accept(typeVisitor));
        }
    }
}
