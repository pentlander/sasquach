package com.pentlander.sasquach.name;

import static com.pentlander.sasquach.Fixtures.foreignMethods;
import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.intValue;
import static com.pentlander.sasquach.Fixtures.name;
import static com.pentlander.sasquach.Fixtures.qualId;
import static com.pentlander.sasquach.Fixtures.range;
import static com.pentlander.sasquach.Fixtures.stringValue;
import static com.pentlander.sasquach.Fixtures.typeId;
import static com.pentlander.sasquach.Fixtures.typeName;
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
import com.pentlander.sasquach.ast.expression.NamedFunction;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.VarReference;
import com.pentlander.sasquach.ast.expression.VariableDeclaration;
import com.pentlander.sasquach.name.MemberScopedNameResolver.QualifiedFunction;
import com.pentlander.sasquach.name.MemberScopedNameResolver.ReferenceDeclaration;
import com.pentlander.sasquach.type.BuiltinType;
import java.util.List;
import java.util.Optional;
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
    var function = FunctionBuilder.builder()
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(new Block(List.of(varDeclA, varDeclB, varDeclC), range()))
        .build();
    var namedFunc = new NamedFunction(id("func"), function);

    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(namedFunc);
    var localRef = (ReferenceDeclaration.Local) result.getVarReference(varReference);

    assertThat(localRef.localVariable()).isEqualTo(varDeclA);
  }

  @Test
  void resolveVariable_module() {
    var id = qualId("OtherModule");
    var module = new ModuleDeclaration(id,
        Struct.moduleStructBuilder(id.moduleName()).range(range()).build(),
        range());
    when(modResolver.resolveModule(name("OtherModule"))).thenReturn(Optional.of(module));

    var varReference = new VarReference(id("OtherModule"));
    var varDeclA = new VariableDeclaration(id("a"), varReference, range());
    var function = FunctionBuilder.builder()
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(new Block(List.of(varDeclA), range()))
        .build();
    var namedFunc = new NamedFunction(id("func"), function);
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(namedFunc);
    var localRef = (ReferenceDeclaration.Module) result.getVarReference(varReference);

    assertThat(localRef.moduleDeclaration()).isEqualTo(module);
  }

  @Test
  void resolveVariable_localFunction() {
    var funcId = id("test");
    var func = FunctionBuilder.builder()
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(stringValue("hi"))
        .build();
    when(modResolver.resolveFunction(name("test"))).thenReturn(Optional.of(new NamedFunction(funcId,
        func)));
    when(modResolver.moduleDeclaration()).thenReturn(new ModuleDeclaration(qualId("foo/bar"),
        null,
        range()));

    var funcCall = new LocalFunctionCall(id("test"), List.of(), range());
    var function = FunctionBuilder.builder()
        .functionSignature(new FunctionSignature(List.of(), typeNode(BuiltinType.VOID), range()))
        .expression(new Block(List.of(funcCall), range()))
        .build();
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(new NamedFunction(id("foo"), function));
    var localFunc = result.getLocalFunctionCallTarget(funcCall);

    assertThat(localFunc).isInstanceOf(QualifiedFunction.class);
  }


  @Test
  void resolveVariable_foreignField() throws Exception {
    when(modResolver.resolveForeignClass(typeName("System"))).thenReturn(Optional.of(System.class));

    var foreignFieldAccess = new ForeignFieldAccess(typeId("System"), id("out"));
    var function = voidFunc("main", foreignFieldAccess);
    var memberResolver = new MemberScopedNameResolver(modResolver);
    var result = memberResolver.resolve(function);
    var field = result.getForeignField(foreignFieldAccess);

    assertThat(field).isEqualTo(System.class.getField("out"));
  }

  @Test
  void resolveVariable_foreignFunctionCall() throws Exception {
    when(modResolver.resolveForeignClass(typeName("String"))).thenReturn(Optional.of(String.class));

    var foreignFunctionCall = new ForeignFunctionCall(typeId("String"),
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
