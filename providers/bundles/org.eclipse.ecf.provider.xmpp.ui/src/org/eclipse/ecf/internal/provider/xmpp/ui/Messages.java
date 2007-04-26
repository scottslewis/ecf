/*******************************************************************************
 * Copyright (c) 2004, 2007 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 ******************************************************************************/

package org.eclipse.ecf.internal.provider.xmpp.ui;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.ecf.internal.provider.xmpp.ui.messages"; //$NON-NLS-1$

	public static String XMPPConnectWizardPage_WIZARD_TITLE;
	public static String XMPPConnectWizardPage_WIZARD_DESCRIPTION;
	public static String XMPPCompoundContributionItem_CHOOSE_FILE;
	public static String XMPPConnectWizard_RECEIVE_ERROR_MESSAGE;
	public static String XMPPSConnectWizardPage_WIZARD_PAGE_TITLE;
	public static String XMPPSConnectWizardPage_WIZARD_PAGE_USERID;
	public static String XMPPSConnectWizardPage_WIZARD_PAGE_STATUS;
	public static String XMPPConnectWizardPage_LABEL_USERID;
	public static String XMPPConnectWizard_FILE_SAVE_TITLE;
	public static String XMPPConnectWizard_SEE_DETAILS;
	public static String XMPPConnectWizardPage_WIZARD_STATUS;
	public static String XMPPConnectWizardPage_USERID_TEMPLATE;
	public static String XMPPConnectWizardPage_WIZARD_PASSWORD;
	public static String XMPPConnectWizard_FILE_RECEIVE_TITLE;
	public static String XMPPConnectWizard_FILE_RECEIVE_MESSAGE;
	public static String XMPPCompoundContributionItem_SEND_FILE;
	public static String XMPPConnectWizard_RECEIVE_ERROR_TITLE;
	public static String XMPPSConnectWizardPage_WIZARD_PAGE_DESCRIPTION;
	public static String XMPPCompoundContributionItem_SEND_ERROR_TITLE;
	public static String XMPPCompoundContributionItem_SEND_ERROR_MESSAGE;
	public static String XMPPSConnectWizardPage_WIZARD_PAGE_TEMPLATE;
	public static String XMPPSConnectWizardPage_WIZARD_PAGE_PASSWORD;
	public static String XMPPCompoundContributionItem_FILE_SEND_REFUSED_TITLE;
	public static String XMPPCompoundContributionItem_FILE_SEND_REFUSED_MESSAGE;

	private Messages() {
	}

	static {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}
}
