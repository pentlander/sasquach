package com.pentlander.sasquach.name;

import static com.pentlander.sasquach.Fixtures.id;
import static com.pentlander.sasquach.Fixtures.qualId;
import static com.pentlander.sasquach.Fixtures.range;
import static com.pentlander.sasquach.Fixtures.stringValue;
import static com.pentlander.sasquach.Fixtures.voidFunc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.pentlander.sasquach.ast.ModuleDeclaration;
import com.pentlander.sasquach.ast.QualifiedIdentifier;
import com.pentlander.sasquach.ast.Scope;
import com.pentlander.sasquach.ast.Use;
import com.pentlander.sasquach.ast.Use.Module;
import com.pentlander.sasquach.ast.expression.Function;
import com.pentlander.sasquach.ast.expression.Struct;
import com.pentlander.sasquach.ast.expression.StructBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModuleScopedNameResolverTest {
  ModuleScopedNameResolver resolver;

  @BeforeEach
  void setUp() {
  }

  @Test
  void resolveForeignClass() {
    var modDecl = new ModuleDeclaration(qualId("Main"),
        Struct.moduleStructBuilder("Main")
            .useList(
                List.of(new Use.Foreign(new QualifiedIdentifier("java/lang/System", range()),
                    id("System"),
                    range()))
            ).range(range()).build(), range());
    resolver = new ModuleScopedNameResolver(modDecl);
    resolver.resolve();

    assertThat(resolver.resolveForeignClass("System").get()).isEqualTo(System.class);
  }

  @Test
  void resolveFields() {
    var field = new Struct.Field(id("foo"), stringValue("bar"));
    var modDecl = new ModuleDeclaration(qualId("Main"),
        Struct.moduleStructBuilder("Main")
            .fields(List.of(field))
            .range(range())
            .build(), range());
    resolver = new ModuleScopedNameResolver(modDecl);
    resolver.resolve();

    var resolvedField = resolver.resolveField("foo").get();
    assertThat(resolvedField).isEqualTo(field);
    assertThat(resolver.getResolver(resolvedField)).isNotNull();
  }

  @Test
  void resolveFunctions() {
    var function = voidFunc(Scope.NULL_SCOPE, "foo", stringValue("bar"));
    var modDecl = new ModuleDeclaration(qualId("Main"),
        Struct.moduleStructBuilder("Main")
            .functions(List.of(function))
            .range(range())
            .build(), range());
    resolver = new ModuleScopedNameResolver(modDecl);
    resolver.resolve();

    var resolvedFunction = resolver.resolveFunction("foo").get();
    assertThat(resolvedFunction).isEqualTo(function);
    assertThat(resolver.getResolver(resolvedFunction)).isNotNull();
  }
}