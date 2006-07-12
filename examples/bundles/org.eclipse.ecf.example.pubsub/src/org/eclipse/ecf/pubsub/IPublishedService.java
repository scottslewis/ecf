/**
 * Copyright (c) 2006 Ecliptical Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Ecliptical Software Inc. - initial API and implementation
 */
package org.eclipse.ecf.pubsub;

import java.util.Map;

import org.eclipse.ecf.core.IIdentifiable;
import org.eclipse.ecf.core.identity.ID;

public interface IPublishedService extends IIdentifiable {

	Map getProperties();
	
	void subscribe(ID containerID);
	
	// TODO this is a bit unsafe -- does not prevent duplicate unsubscription
	// -- perhaps we should create a token (ID) in subscribe() and require it here?
	void unsubscribe(ID containerID);
}
