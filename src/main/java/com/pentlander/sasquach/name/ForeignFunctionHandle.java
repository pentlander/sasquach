package com.pentlander.sasquach.name;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.Executable;

public record ForeignFunctionHandle(DirectMethodHandleDesc methodHandleDesc, Executable executable) {}
