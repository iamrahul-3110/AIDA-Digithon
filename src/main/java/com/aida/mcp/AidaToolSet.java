package com.aida.mcp;

/**
 * Marker for classes holding AIDA {@code @Tool} methods. Every implementation is a {@code @Component};
 * {@link ToolConfig} registers all of them, so adding a tool class never edits a shared file. FROZEN.
 */
public interface AidaToolSet {
}
