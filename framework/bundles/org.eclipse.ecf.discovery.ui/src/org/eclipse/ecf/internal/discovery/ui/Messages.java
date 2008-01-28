/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.internal.discovery.ui;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.ecf.internal.discovery.ui.messages"; //$NON-NLS-1$

	public static String DiscoveryView_Services;
	public static String DiscoveryView_AddressLabel;
	public static String DiscoveryView_TypeLabel;
	public static String DiscoveryView_PortLabel;
	public static String DiscoveryView_PriorityLabel;
	public static String DiscoveryView_WeightLabel;
	public static String DiscoveryView_REFRESH_ACTION_LABEL;

	public static String DiscoveryView_REFRESH_SERVICES_TOOLTIPTEXT;

	public static String DiscoveryView_RequestInfo;
	public static String DiscoveryView_RequestInfoTooltip;
	public static String DiscoveryView_RegisterType;
	public static String DiscoveryView_RegisterTypeTooltip;
	public static String DiscoveryView_ConnectTo;
	public static String DiscoveryView_ConnectToTooltip;
	public static String DiscoveryView_StopDiscoveryTitle;
	public static String DiscoveryView_StopDiscoveryDescription;
	public static String DiscoveryView_StopDiscovery;
	public static String DiscoveryView_StopDiscoveryTooltip;
	public static String DiscoveryView_StartDiscovery;
	public static String DiscoveryView_StartDiscoveryTooltip;
	public static String DiscoveryView_ConnectToService;

	public static String DiscoveryView_EXCEPTION_CREATING_SERVICEACCESSHANDLER;

	public static String DiscoveryView_NO_SERVICE_HANDLER_LABEL;

	public static String DiscoveryViewContentProvider_CONNECT_REQUIRES_PASSWORD_LABEL;

	public static String DiscoveryViewContentProvider_CONNECT_TARGET_LABEL;

	public static String DiscoveryViewContentProvider_CONTAINER_FACTORY_LABEL;

	public static String DiscoveryViewContentProvider_CONTAINER_SERVICE_INFO_LABEL;

	public static String DiscoveryViewContentProvider_NAME_LABEL;

	public static String DiscoveryViewContentProvider_SERVICE_NAME_LABEL;

	public static String DiscoveryViewContentProvider_SERVICE_NAMESPACE_LABEL;

	public static String DiscoveryViewContentProvider_SERVICE_PROPERTIES_LABEL;

	public static String DiscoveryViewContentProvider_TYPE_INTERNAL_LABEL;

	public static String DiscoveryViewContentProvider_TYPE_NAMESPACE_LABEL;

	public static String HttpServiceAccessHandler_EXCEPTION_CREATEBROWSER;

	public static String HttpServiceAccessHandler_MENU_TEXT;

	static {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

}
