package com.pentlander.sasquach.name;

import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.qualId;
import static com.pentlander.sasquach.Fixtures.range;
import static com.pentlander.sasquach.Fixtures.stringValue;
import static com.pentlander.sasquach.Fixtures.voidFunc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedModuleId;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.expression.LiteralStruct;
import com.pentlander.sasquach.ast.expression.Struct;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModuleScopedNameResolverTest {
  ModuleResolver moduleResolver;
  ModuleScopedNameResolver resolver;

  @BeforeEach
  void setUp() {
    moduleResolver = mock(ModuleResolver.class);
  }

  @Test
  void resolveForeignClass() {
    var id = qualId("Main");
    var modDecl = new ModuleDeclaration(
        id,
        Struct.moduleStructBuilder(id.moduleName())
            .useList(List.of(new Use.Foreign(QualifiedModuleId.fromString(
                "java/lang/System",
                range()), id("System"), range())))
            .range(range())
            .build(),
        range());
    resolver = new ModuleScopedNameResolver(modDecl, moduleResolver);
    resolver.resolve();

    assertThat(resolver.resolveForeignClass("System").get()).isEqualTo(System.class);
  }

  @Test
  void resolveFields() {
    var id = qualId("Main");
    var field = new LiteralStruct.Field(id("foo"), stringValue("bar"));
    var modDecl = new ModuleDeclaration(id,
        Struct.moduleStructBuilder(id.moduleName()).fields(List.of(field)).range(range()).build(),
        range());
    resolver = new ModuleScopedNameResolver(modDecl, moduleResolver);
    resolver.resolve();

    var resolvedField = resolver.resolveField("foo").get();
    assertThat(resolvedField).isEqualTo(field);
    assertThat(resolver.getResolver(resolvedField)).isNotNull();
  }

  @Test
  void resolveFunctions() {
    var id = qualId("Main");
    var function = voidFunc("foo", stringValue("bar"));
    var modDecl = new ModuleDeclaration(
        id,
        Struct.moduleStructBuilder(id.moduleName())
            .functions(List.of(function))
            .range(range())
            .build(),
        range());
    resolver = new ModuleScopedNameResolver(modDecl, moduleResolver);
    resolver.resolve();

    var resolvedFunction = resolver.resolveFunction("foo").get();
    assertThat(resolvedFunction).isEqualTo(function);
    assertThat(resolver.getResolver(resolvedFunction.function())).isNotNull();
  }
}
