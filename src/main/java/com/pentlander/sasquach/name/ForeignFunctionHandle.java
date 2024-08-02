package com.pentlander.sasquach.name;

import com.pentlander.sasquach.ast.InvocationKind;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Executable;

public record ForeignFunctionHandle(MethodHandle methodHandle, InvocationKind invocationKind,
                                    Executable executable) {}
