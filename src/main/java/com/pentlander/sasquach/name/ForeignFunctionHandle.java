package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.InvocationKind;
import java.lang.invoke.MethodHandle;

public record ForeignFunctionHandle(MethodHandle methodHandle, InvocationKind invocationKind) {}
