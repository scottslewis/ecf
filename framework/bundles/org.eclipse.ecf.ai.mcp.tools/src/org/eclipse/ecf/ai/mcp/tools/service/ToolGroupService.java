/****************************************************************************
 * Copyright (c) 2025 Composent, Inc.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
  * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.ecf.ai.mcp.tools.service;

import java.util.List;

import org.eclipse.ecf.ai.mcp.tools.util.ToolDescription;

public interface ToolGroupService {

	default List<ToolDescription> getToolDescriptions(String interfaceClassName) {
		return ToolDescription.fromService(this, interfaceClassName);
	}
}
