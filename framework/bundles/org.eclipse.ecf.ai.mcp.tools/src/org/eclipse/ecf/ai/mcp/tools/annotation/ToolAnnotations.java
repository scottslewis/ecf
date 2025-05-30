package org.eclipse.ecf.ai.mcp.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Supports the addition of ToolAnnotations to Tool spec in the MCP schema
 * (draft as of 5/18/2025) located <a href=
 * "https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/schema/draft/schema.json#L2164">here</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolAnnotations {

	String title() default "";

	boolean destructiveHint() default false;

	boolean idempotentHint() default false;

	boolean openWorldHint() default false;

	boolean readOnlyHint() default false;

}
