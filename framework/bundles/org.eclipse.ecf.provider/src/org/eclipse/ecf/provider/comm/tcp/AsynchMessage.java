/****************************************************************************
* Copyright (c) 2004 Composent, Inc. and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*    Composent, Inc. - initial API and implementation
*****************************************************************************/

package org.eclipse.ecf.provider.comm.tcp;

import java.io.Serializable;

public class AsynchMessage implements Serializable {
    Serializable data;

    protected AsynchMessage() {
    }

    protected AsynchMessage(Serializable data) {
        this.data = data;
    }

    Serializable getData() {
        return data;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer("AsynchMessage[");
        buf.append(data).append("]");
        return buf.toString();
    }
}