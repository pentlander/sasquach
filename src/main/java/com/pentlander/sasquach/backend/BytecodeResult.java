package com.pentlander.sasquach.backend;

import java.util.Map;

public record BytecodeResult(Map<String, byte[]> generatedClasses) {}
