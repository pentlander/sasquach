package com.pentlander.sasquach.name;

import static com.pentlander.sasquach.Fixtures.foreignMethods;
import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.intValue;
import static com.pentlander.sasquach.Fixtures.qualId;
import static com.pentlander.sasquach.Fixtures.range;
import static com.pentlander.sasquach.Fixtures.stringValue;
import static com.pentlander.sasquach.Fixtures.typeNode;
import static com.pentlander.sasquach.Fixtures.voidFunc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pentlander.sasquach.Fixtures;
import com.pentlander.sasquach.ast.FunctionSignature;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.expression.Block;
import com.pentlander.sasquach.ast.expression.ForeignFieldAccess;
import com.pentlander.sasquach.ast.expression.ForeignFunctionCall;
import com.pentlander.sasquach.ast.expression.FunctionBuilder;
import com.pentlander.sasquach.ast.expression.LocalFunctionCall;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.type.BuiltinType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemberScopedNameResolverTest {
  ModuleScopedNameResolver modResolver;

  @BeforeEach
  void setUp() {
    modResolver = Mockito.mock(ModuleScopedNameResolver.class);
  }

  @Test
  void resolveVariableDeclarations() {
    var varReference = new VarReference(id("a"));
    var varDeclA = new VariableDeclaration(id("a"), Fixtures.intValue(10), range());
    var varDeclB = new VariableDeclaration(id("b"), varReference, range());
    var varDeclC = new VariableDeclaration(id("c"), varReference, range());
    var function = FunctionBuilder.builder().id(id("main"))
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(new Block(List.of(varDeclA, varDeclB, varDeclC), range()))
        .build();
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(function);
    var localRef = (ReferenceDeclaration.Local) result.getVarReference(varReference);

    assertThat(localRef.localVariable()).isEqualTo(varDeclA);
    assertThat(localRef.index()).isEqualTo(0);

    assertThat(result.getVarIndex(varDeclA)).isEqualTo(0);
    assertThat(result.getVarIndex(varDeclB)).isEqualTo(1);
    assertThat(result.getVarIndex(varDeclC)).isEqualTo(2);
  }

  @Test
  void resolveVariable_module() {
    var module = new ModuleDeclaration(qualId("OtherModule"),
        Struct.moduleStruct(
            "OtherModule",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            range()),
        range());
    when(modResolver.resolveModule("OtherModule")).thenReturn(Optional.of(module));

    var varReference = new VarReference(id("OtherModule"));
    var varDeclA = new VariableDeclaration(id("a"), varReference, range());
    var function = FunctionBuilder.builder().id(id("main"))
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(new Block(List.of(varDeclA), range()))
        .build();
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(function);
    var localRef = (ReferenceDeclaration.Module) result.getVarReference(varReference);

    assertThat(localRef.moduleDeclaration()).isEqualTo(module);
    assertThat(result.getVarIndex(varDeclA)).isEqualTo(0);
  }

  @Test
  void resolveVariable_localFunction() {
    var func = FunctionBuilder.builder()
        .id(id("test"))
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(stringValue("hi"))
        .build();
    when(modResolver.resolveFunction("test")).thenReturn(Optional.of(func));
    when(modResolver.moduleDeclaration()).thenReturn(new ModuleDeclaration(qualId("foo/bar"),
        null, range()));

    var funcCall = new LocalFunctionCall(id("test"), List.of(), range());
    var function = FunctionBuilder.builder().id(id("main"))
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(new Block(List.of(funcCall), range()))
        .build();
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(function);
    var localFunc = result.getLocalFunction(funcCall);

    assertThat(localFunc.function()).isEqualTo(func);
  }


  @Test
  void resolveVariable_foreignField() throws Exception {
    when(modResolver.resolveForeignClass("System")).thenReturn(Optional.of(System.class));

    var foreignFieldAccess = new ForeignFieldAccess(id("System"), id("out"));
    var function = voidFunc("main", foreignFieldAccess);
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(function);
    var field = result.getForeignField(foreignFieldAccess);

    assertThat(field).isEqualTo(System.class.getField("out"));
  }

  @Test
  void resolveVariable_foreignFunctionCall() throws Exception {
    when(modResolver.resolveForeignClass("String")).thenReturn(Optional.of(String.class));

    var foreignFunctionCall = new ForeignFunctionCall(id("String"),
        id("valueOf"),
        List.of(intValue("5")),
        range());
    var function = voidFunc("main", foreignFunctionCall);
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(function);
    ForeignFunctions executables = result.getForeignFunction(foreignFunctionCall);
    var actualForeignFunctions = foreignMethods(String.class,
        m -> m.getName().equals("valueOf") && m.getParameterCount() == 1);

    assertThat(executables.ownerClass()).isEqualTo(actualForeignFunctions.ownerClass());
    // TODO: Fix test
//    assertThat(executables.functions()).allMatch(ffHandle -> ffHandle.invocationKind().equals(ac))
  }
}