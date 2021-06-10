package com.pentlander.sasquach;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pentlander.sasquach.ast.*;
import org.antlr.v4.runtime.*;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.pentlander.sasquach.ast.BinaryExpression.*;

public class Main {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        try {
            var sasquachPath = Paths.get(args[0]);
            var filename = sasquachPath.getFileName().toString();
            var compilationUnit = new Parser().getCompilationUnit(sasquachPath);
            System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(compilationUnit));

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
            var expressions = function.expressions();
            expressions.forEach(expr -> exprGenerator.generate(expr, function.scope()));
            var returnExpr = function.returnExpression();
            if (returnExpr != null) {
                exprGenerator.generate(returnExpr, function.scope());
                Type type = returnExpr.type();
                if (type instanceof BuiltinType builtinType) {
                    int opcode = switch (builtinType) {
                        case BOOLEAN, INT, CHAR, BYTE, SHORT -> Opcodes.IRETURN;
                        case LONG -> Opcodes.LRETURN;
                        case FLOAT -> Opcodes.FRETURN;
                        case DOUBLE -> Opcodes.DRETURN;
                        case STRING, STRING_ARR -> Opcodes.ARETURN;
                        case VOID -> Opcodes.RETURN;
                    };
                    methodVisitor.visitInsn(opcode);
                }
            } else {
                methodVisitor.visitInsn(Opcodes.RETURN);
            }
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
            if (expression instanceof PrintStatement printStatement) {
                methodVisitor.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                generate(printStatement.expression(), scope);
                String descriptor = "(%s)V".formatted(printStatement.expression().type().descriptor());
                StructType owner = new StructType("java.io.PrintStream");
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner.internalName(), "println", descriptor, false);
            } else if (expression instanceof  VariableDeclaration varDecl) {
                var varDeclExpr = varDecl.expression();
                int idx = scope.findIdentifierIdx(varDecl.name());
                generate(varDeclExpr, scope);
                var type = varDeclExpr.type();
                if (type instanceof BuiltinType builtinType) {
                    Integer opcode = switch (builtinType) {
                        case BOOLEAN, INT, BYTE, CHAR, SHORT -> Opcodes.ISTORE;
                        case LONG -> Opcodes.LSTORE;
                        case FLOAT -> Opcodes.FSTORE;
                        case DOUBLE -> Opcodes.DSTORE;
                        case STRING -> Opcodes.ASTORE;
                        case STRING_ARR -> Opcodes.AASTORE;
                        case VOID -> null;
                    };
                    if (opcode != null) {
                        methodVisitor.visitVarInsn(opcode, idx);
                    }
                }
            } else if (expression instanceof Identifier id) {
                int idx = scope.findIdentifierIdx(id.name());
                if (idx != -1 && id.type() instanceof BuiltinType builtinType) {
                    Integer opcode = switch (builtinType) {
                        case BOOLEAN, INT, CHAR, BYTE, SHORT -> Opcodes.ILOAD;
                        case LONG -> Opcodes.LLOAD;
                        case FLOAT -> Opcodes.FLOAD;
                        case DOUBLE -> Opcodes.DLOAD;
                        case STRING -> Opcodes.ALOAD;
                        case STRING_ARR -> Opcodes.AALOAD;
                        case VOID -> null;
                    };
                    if (opcode != null) {
                        methodVisitor.visitVarInsn(opcode, idx);
                    } else {
                        methodVisitor.visitInsn(Opcodes.ACONST_NULL);
                    }
                }
            } else if (expression instanceof Value value) {
                var type = value.type();
                var literal = value.value();
                if (type instanceof BuiltinType builtinType) {
                    switch (builtinType) {
                        case BOOLEAN, INT, CHAR, BYTE, SHORT -> {
                            int intValue = Integer.parseInt(literal);
                            methodVisitor.visitIntInsn(Opcodes.BIPUSH, intValue);
                        }
                        case LONG -> {
                            long longValue = Long.parseLong(literal);
                            methodVisitor.visitLdcInsn(longValue);
                        }
                        case FLOAT -> {
                            float floatValue = Float.parseFloat(literal);
                            methodVisitor.visitLdcInsn(floatValue);
                        }
                        case DOUBLE -> {
                            double doubleValue = Double.parseDouble(literal);
                            methodVisitor.visitLdcInsn(doubleValue);
                        }
                        case STRING -> methodVisitor.visitLdcInsn(literal);
                        case STRING_ARR -> {}
                        case VOID -> methodVisitor.visitInsn(Opcodes.ACONST_NULL);
                    }
                }
            } else if (expression instanceof FunctionParameter funcParam) {
                if (funcParam.type() == BuiltinType.INT) {
                    System.out.println("Visited function param: " + funcParam);
                    methodVisitor.visitVarInsn(Opcodes.ILOAD, funcParam.index());
                }
            } else if (expression instanceof FunctionCall funcCall) {
                funcCall.arguments().forEach(arg -> generate(arg, scope));
                String descriptor = DescriptorFactory.getMethodDescriptor(funcCall.signature());
                Type owner = Objects.requireNonNullElseGet(funcCall.owner(), () -> new StructType(scope.getClassName()));
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, owner.internalName(), funcCall.functionName(), descriptor, false);
            } else if (expression instanceof BinaryExpression binExpr) {
                generate(binExpr.left(), scope);
                generate(binExpr.right(), scope);
                if (expression instanceof MathExpression mathExpr) {
                    int opcode = switch (mathExpr.operator()) {
                        case PLUS -> Opcodes.IADD;
                        case MINUS -> Opcodes.ISUB;
                        case ASTERISK -> Opcodes.IMUL;
                        case DIVIDE -> Opcodes.IDIV;
                    };
                    methodVisitor.visitInsn(opcode);
                }
            } else if (expression instanceof IfExpression ifExpr) {
                generate(ifExpr.condition(), scope);
                var falseLabel = new Label();
                var endLabel = new Label();
                methodVisitor.visitJumpInsn(Opcodes.IFEQ, falseLabel);
                generateBlock(ifExpr.trueBlock(), scope);
                methodVisitor.visitJumpInsn(Opcodes.GOTO, endLabel);
                methodVisitor.visitLabel(falseLabel);
                generateBlock(ifExpr.falseBlock(), scope);
                methodVisitor.visitLabel(endLabel);
            }
        }

        private void generateBlock(Block block, Scope scope) {
            block.expressions().forEach(expr -> generate(expr, scope));
            generate(block.returnExpression(), scope);
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

    record CompileError(String message, Position position) {}

    record Position(int line, int column) {}

    static class SasquachTreeWalkErrorListener extends BaseErrorListener {
        private final List<CompileError> compileErrors = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            compileErrors.add(new CompileError(msg, new Position(line, charPositionInLine)));
        }

        public List<CompileError> getCompileErrors() {
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

            List<Expression> expressions = ctx.block().blockStatement().stream()
                    .map(blockCtx -> blockCtx.accept(new ExpressionVisitor(scope)))
                    .toList();

            Expression returnExpr = null;
            if (ctx.block().returnExpression != null) {
                returnExpr = ctx.block().returnExpression.accept(new ExpressionVisitor(scope));
            }

            return new Function(scope, name, returnType, parameters, expressions, returnExpr);
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
        public Expression visitValueLiteral(SasquachParser.ValueLiteralContext ctx) {
            String value = ctx.getText();
            var visitor = new TypeVisitor();
            Type type = ctx.accept(visitor);
            return new Value(type, value);
        }

        @Override
        public Expression visitParenExpression(SasquachParser.ParenExpressionContext ctx) {
            return ctx.expression().accept(this);
        }

        @Override
        public Expression visitBinaryOperation(SasquachParser.BinaryOperationContext ctx) {
            String operatorString = ctx.operator.getText();
            var visitor = new ExpressionVisitor(scope);
            var leftExpr = ctx.left.accept(visitor);
            var rightExpr = ctx.right.accept(visitor);
            return new MathExpression(Operator.fromString(operatorString), leftExpr, rightExpr);
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
        public Expression visitIfExpression(SasquachParser.IfExpressionContext ctx) {
            var ifBlock = ctx.ifBlock();
            var ifCondition = ifBlock.ifCondition.accept(this);
            var blockVisitor = new BlockVisitor(scope);
            var trueBlock = ifBlock.trueBlock.accept(blockVisitor);
            Block falseBlock = null;
            if (ifBlock.falseBlock != null) {
                falseBlock = ifBlock.falseBlock.accept(blockVisitor);
            }
            return new IfExpression(ifCondition, trueBlock, falseBlock);
        }

        @Override
        public Expression visitVariableDeclaration(SasquachParser.VariableDeclarationContext ctx) {
            var idName = ctx.identifier().getText();
            Expression expr = ctx.expression().accept(new ExpressionVisitor(scope));
            var identifier = new Identifier(idName, expr);
            scope.addIdentifier(identifier);
            return new VariableDeclaration(identifier.name(), identifier.expression(), ctx.index);
        }
    }

    static class BlockVisitor extends SasquachBaseVisitor<Block> {
        private final Scope scope;

        BlockVisitor(Scope scope) {
            this.scope = scope;
        }

        @Override
        public Block visitBlock(SasquachParser.BlockContext ctx) {
            List<Expression> expressions = ctx.blockStatement().stream()
                    .map(blockCtx -> blockCtx.accept(new ExpressionVisitor(scope)))
                    .toList();

            Expression returnExpr = null;
            if (ctx.returnExpression != null) {
                returnExpr = ctx.returnExpression.accept(new ExpressionVisitor(scope));
            }

            return new Block(expressions, returnExpr);
        }
    }

    static class TypeVisitor extends SasquachBaseVisitor<Type> {
        @Override
        public Type visitIntLiteral(SasquachParser.IntLiteralContext ctx) {
            return BuiltinType.INT;
        }

        @Override
        public Type visitStringLiteral(SasquachParser.StringLiteralContext ctx) {
            return BuiltinType.STRING;
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
