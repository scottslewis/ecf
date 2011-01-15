/*******************************************************************************
 * Copyright (c) 2010-2011 Composent, Inc. and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Composent, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.ecf.osgi.services.remoteserviceadmin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.ecf.core.ContainerConnectException;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.core.IContainerManager;
import org.eclipse.ecf.core.identity.ID;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.internal.osgi.services.remoteserviceadmin.Activator;
import org.eclipse.ecf.internal.osgi.services.remoteserviceadmin.DebugOptions;
import org.eclipse.ecf.internal.osgi.services.remoteserviceadmin.LogUtility;
import org.eclipse.ecf.internal.osgi.services.remoteserviceadmin.PropertiesUtil;
import org.eclipse.ecf.remoteservice.IOSGiRemoteServiceContainerAdapter;
import org.eclipse.ecf.remoteservice.IRemoteService;
import org.eclipse.ecf.remoteservice.IRemoteServiceContainer;
import org.eclipse.ecf.remoteservice.IRemoteServiceContainerAdapter;
import org.eclipse.ecf.remoteservice.IRemoteServiceID;
import org.eclipse.ecf.remoteservice.IRemoteServiceListener;
import org.eclipse.ecf.remoteservice.IRemoteServiceReference;
import org.eclipse.ecf.remoteservice.IRemoteServiceRegistration;
import org.eclipse.ecf.remoteservice.events.IRemoteServiceEvent;
import org.eclipse.ecf.remoteservice.events.IRemoteServiceUnregisteredEvent;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.remoteserviceadmin.EndpointPermission;
import org.osgi.service.remoteserviceadmin.RemoteServiceAdminListener;
import org.osgi.util.tracker.ServiceTracker;

public class RemoteServiceAdmin implements
		org.osgi.service.remoteserviceadmin.RemoteServiceAdmin {

	public static final String SERVICE_PROP = "org.eclipse.ecf.rsa";

	private Bundle bundle;

	private boolean hostAutoCreateContainer = new Boolean(
			System.getProperty(
					"org.eclipse.ecf.osgi.services.remoteserviceadmin.hostAutoCreateContainer",
					"true")).booleanValue();
	private String[] hostDefaultConfigTypes = new String[] { System
			.getProperty(
					"org.eclipse.ecf.osgi.services.remoteserviceadmin.hostDefaultConfigType",
					"ecf.generic.server") };

	private boolean consumerAutoCreateContainer = new Boolean(
			System.getProperty(
					"org.eclipse.ecf.osgi.services.remoteserviceadmin.consumerAutoCreateContainer",
					"true")).booleanValue();

	private PackageVersionComparator packageVersionComparator;

	private ServiceTracker packageAdminTracker;
	private Object packageAdminTrackerLock = new Object();

	private Object remoteServiceAdminListenerTrackerLock = new Object();
	private ServiceTracker remoteServiceAdminListenerTracker;

	private Collection<ExportRegistration> exportedRegistrations = new ArrayList<ExportRegistration>();
	private Collection<ImportRegistration> importedRegistrations = new ArrayList<ImportRegistration>();

	protected BundleContext getContext() {
		return bundle.getBundleContext();
	}

	protected Bundle getBundle() {
		return bundle;
	}

	private boolean removeExportRegistration(
			ExportRegistration exportRegistration) {
		synchronized (exportedRegistrations) {
			boolean removed = exportedRegistrations.remove(exportRegistration);
			trace("removeExportRegistration", "exportRegistration="
					+ exportRegistration + " exportedRegistrations="
					+ exportedRegistrations + " removed=" + removed);
			return removed;
		}
	}

	private boolean removeImportRegistration(
			ImportRegistration importRegistration) {
		synchronized (importedRegistrations) {
			boolean removed = importedRegistrations.remove(importRegistration);
			trace("removeImportRegistration", "importRegistration="
					+ importRegistration + " importedRegistrations="
					+ importedRegistrations + " removed=" + removed);
			return removed;
		}
	}

	public RemoteServiceAdmin(Bundle bundle) {
		this.bundle = bundle;
		Assert.isNotNull(bundle);
		setupDefaultContainerSelectors();
		hostContainerSelector = new HostContainerSelector(
				hostDefaultConfigTypes, hostAutoCreateContainer);
		consumerContainerSelector = new ConsumerContainerSelector(
				consumerAutoCreateContainer);
		// create and register a default package version comparator
		packageVersionComparator = new PackageVersionComparator();
	}

	private HostContainerSelector hostContainerSelector;
	private ServiceRegistration hostContainerSelectorRegistration;

	private ConsumerContainerSelector consumerContainerSelector;
	private ServiceRegistration consumerContainerSelectorRegistration;

	private void setupDefaultContainerSelectors() {
		// Only setup defaults if it hasn't already been done by some other
		// Remote Service Admin instance
		Properties props = new Properties();
		props.put(org.osgi.framework.Constants.SERVICE_RANKING, new Integer(
				Integer.MIN_VALUE));
		// host container selector. register default only if none exist
		ServiceReference[] hostContainerSelectorRefs = null;
		try {
			hostContainerSelectorRefs = getContext().getServiceReferences(
					IHostContainerSelector.class.getName(), null);
		} catch (InvalidSyntaxException e) {
			// will not happen
		}
		if (hostContainerSelectorRefs == null
				|| hostContainerSelectorRefs.length == 0) {
			hostContainerSelector = new HostContainerSelector(
					hostDefaultConfigTypes, hostAutoCreateContainer);
			hostContainerSelectorRegistration = getContext().registerService(
					IHostContainerSelector.class.getName(),
					hostContainerSelector, (Dictionary) props);
		}
		// consumer container selector. register default only if none exist
		ServiceReference[] consumerContainerSelectorRefs = null;
		try {
			consumerContainerSelectorRefs = getContext().getServiceReferences(
					IConsumerContainerSelector.class.getName(), null);
		} catch (InvalidSyntaxException e) {
			// will not happen
		}
		if (consumerContainerSelectorRefs == null
				|| consumerContainerSelectorRefs.length == 0) {
			consumerContainerSelector = new ConsumerContainerSelector(
					consumerAutoCreateContainer);
			consumerContainerSelectorRegistration = getContext()
					.registerService(
							IConsumerContainerSelector.class.getName(),
							consumerContainerSelector, (Dictionary) props);
		}
	}

	private void closeDefaultContainerSelectors() {
		if (hostContainerSelectorRegistration != null) {
			hostContainerSelectorRegistration.unregister();
			hostContainerSelectorRegistration = null;
		}
		if (hostContainerSelector != null) {
			hostContainerSelector.close();
			hostContainerSelector = null;
		}
		if (consumerContainerSelectorRegistration != null) {
			consumerContainerSelectorRegistration.unregister();
			consumerContainerSelectorRegistration = null;
		}
		if (consumerContainerSelector != null) {
			consumerContainerSelector.close();
			consumerContainerSelector = null;
		}
	}

	// RemoteServiceAdmin service interface impl methods
	public Collection<org.osgi.service.remoteserviceadmin.ExportRegistration> exportService(
			ServiceReference serviceReference,
			Map<String, Object> overridingProperties) {

		trace("exportService", "serviceReference=" + serviceReference
				+ ",properties=" + overridingProperties);

		overridingProperties = (overridingProperties == null) ? Collections.EMPTY_MAP
				: overridingProperties;

		// First get exported interfaces
		String[] exportedInterfaces = (String[]) overridingProperties
				.get(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_EXPORTED_INTERFACES);
		// As per 122.5.1 we only use the OBJECTCLASS value from the
		// serviceReference, not from the overridingProperties map
		if (exportedInterfaces == null)
			exportedInterfaces = PropertiesUtil
					.getExportedInterfaces(serviceReference);
		// If exportedInterfaces is still null, we throw
		if (exportedInterfaces == null)
			throw new IllegalArgumentException(
					org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_EXPORTED_INTERFACES
							+ " not set");

		// Get optional service property for exported configs
		String[] exportedConfigs = PropertiesUtil
				.getStringArrayFromPropertyValue(serviceReference
						.getProperty(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_EXPORTED_CONFIGS));
		// Get all intents (service.intents, service.exported.intents,
		// service.exported.intents.extra)
		String[] serviceIntents = PropertiesUtil
				.getServiceIntents(serviceReference);

		// Get a host container selector
		IHostContainerSelector hostContainerSelector = getHostContainerSelector();
		if (hostContainerSelector == null) {
			logError("handleServiceRegistering",
					"No hostContainerSelector available");
			return Collections.EMPTY_LIST;
		}
		// select ECF remote service containers that match given exported
		// interfaces, configs, and intents
		IRemoteServiceContainer[] rsContainers = hostContainerSelector
				.selectHostContainers(serviceReference, exportedInterfaces,
						exportedConfigs, serviceIntents);
		// If none found, log a warning and we're done
		if (rsContainers == null || rsContainers.length == 0) {
			logWarning(
					"handleServiceRegistered", "No remote service containers found for serviceReference=" //$NON-NLS-1$
							+ serviceReference
							+ ". Remote service NOT EXPORTED"); //$NON-NLS-1$
			return Collections.EMPTY_LIST;
		}
		Collection<ExportRegistration> exportRegistrations = new ArrayList<ExportRegistration>();
		synchronized (exportedRegistrations) {
			for (int i = 0; i < rsContainers.length; i++) {
				ExportRegistration exportRegistration = null;
				// If we've already got an export endpoint
				// for this service reference/containerID combination,
				// then create an ExportRegistration that uses the endpoint
				ExportEndpoint exportEndpoint = findExistingExportEndpoint(
						serviceReference, rsContainers[i].getContainer()
								.getID());
				// If we've already got one, then create a new
				// ExportRegistration for it
				if (exportEndpoint != null)
					exportRegistration = new ExportRegistration(exportEndpoint);
				else {
					exportRegistration = new ExportRegistration(
							exportService(serviceReference,
									overridingProperties, exportedInterfaces,
									serviceIntents, rsContainers[i]));
					// If no exception, we add it to our known set of exported
					// registrations
					if (exportRegistration.getException() == null)
						exportedRegistrations.add(exportRegistration);
				}
				// We add it to the results in either case
				exportRegistrations.add(exportRegistration);
			}
		}
		// publish all exportRegistrations
		for (ExportRegistration exportReg : exportRegistrations)
			publishExportEvent(exportReg);
		// and return
		return new ArrayList<org.osgi.service.remoteserviceadmin.ExportRegistration>(
				exportRegistrations);
	}

	public class ExportEndpoint {

		private ServiceReference serviceReference;
		private EndpointDescription endpointDescription;

		private IRemoteServiceRegistration rsRegistration;
		private ExportReference exportReference;
		private Set<ExportRegistration> exportRegistrations = new HashSet<ExportRegistration>();

		private Throwable exception;

		ExportEndpoint(ServiceReference serviceReference,
				EndpointDescription endpointDescription,
				IRemoteServiceRegistration reg) {
			Assert.isNotNull(serviceReference);
			this.serviceReference = serviceReference;
			Assert.isNotNull(endpointDescription);
			this.endpointDescription = endpointDescription;

			Assert.isNotNull(reg);
			this.rsRegistration = reg;
			this.exportReference = new ExportReference(serviceReference,
					endpointDescription);
		}

		ExportEndpoint(ServiceReference serviceReference,
				EndpointDescription endpointDescription, Throwable t) {
			Assert.isNotNull(serviceReference);
			this.serviceReference = serviceReference;
			Assert.isNotNull(endpointDescription);
			this.endpointDescription = endpointDescription;
			this.exception = t;
		}

		synchronized Throwable getException() {
			return exception;
		}

		synchronized ID getContainerID() {
			return endpointDescription.getContainerID();
		}

		synchronized ServiceReference getServiceReference() {
			return serviceReference;
		}

		synchronized EndpointDescription getEndpointDescription() {
			return endpointDescription;
		}

		synchronized ExportReference getExportReference() {
			return exportReference;
		}

		synchronized IRemoteServiceRegistration getRemoteServiceRegistration() {
			return rsRegistration;
		}

		synchronized boolean add(ExportRegistration exportRegistration) {
			return this.exportRegistrations.add(exportRegistration);
		}

		synchronized boolean close(ExportRegistration exportRegistration) {
			boolean removed = this.exportRegistrations
					.remove(exportRegistration);
			if (removed && exportRegistrations.size() == 0) {
				if (rsRegistration != null) {
					rsRegistration.unregister();
					rsRegistration = null;
				}
				if (exportReference != null) {
					exportReference.close();
					exportReference = null;
				}
			}
			return removed;
		}

		public synchronized String toString() {
			return "ExportEndpoint [rsRegistration=" + rsRegistration
					+ ", exportReference=" + exportReference + "]";
		}

	}

	public class ExportRegistration implements
			org.osgi.service.remoteserviceadmin.ExportRegistration {

		private ExportEndpoint exportEndpoint;

		ExportRegistration(ExportEndpoint exportEndpoint) {
			Assert.isNotNull(exportEndpoint);
			this.exportEndpoint = exportEndpoint;
			// Add ourselves to this exported endpoint
			this.exportEndpoint.add(this);
		}

		synchronized ID getContainerID() {
			return (exportEndpoint == null) ? null : exportEndpoint
					.getContainerID();
		}

		synchronized ServiceReference getServiceReference() {
			return (exportEndpoint == null) ? null : exportEndpoint
					.getServiceReference();
		}

		public synchronized org.osgi.service.remoteserviceadmin.ExportReference getExportReference() {
			Throwable t = getException();
			if (t != null)
				throw new IllegalStateException(
						"Cannot get export reference as export registration is invalid",
						t);
			return (exportEndpoint == null) ? null : exportEndpoint
					.getExportReference();
		}

		synchronized boolean match(ServiceReference serviceReference) {
			return match(serviceReference, null);
		}

		synchronized boolean match(ServiceReference serviceReference,
				ID containerID) {
			ServiceReference ourServiceReference = getServiceReference();
			if (ourServiceReference == null)
				return false;
			boolean serviceReferenceCompare = ourServiceReference
					.equals(serviceReference);
			// If the second parameter is null, then we compare only on service
			// references
			if (containerID == null)
				return serviceReferenceCompare;
			ID ourContainerID = getContainerID();
			if (ourContainerID == null)
				return false;
			return serviceReferenceCompare
					&& ourContainerID.equals(containerID);
		}

		synchronized ExportEndpoint getExportEndpoint(
				ServiceReference serviceReference, ID containerID) {
			return match(serviceReference, containerID) ? exportEndpoint : null;
		}

		synchronized IRemoteServiceRegistration getRemoteServiceRegistration() {
			return (exportEndpoint == null) ? null : exportEndpoint
					.getRemoteServiceRegistration();
		}

		synchronized EndpointDescription getEndpointDescription() {
			return (exportEndpoint == null) ? null : exportEndpoint
					.getEndpointDescription();
		}

		public void close() {
			boolean closed = false;
			Throwable t = null;
			org.osgi.service.remoteserviceadmin.ExportReference exportReference = null;
			EndpointDescription endpointDescription = null;
			synchronized (this) {
				if (exportEndpoint != null) {
					t = getException();
					exportReference = (t == null) ? getExportReference() : null;
					endpointDescription = getEndpointDescription();
					closed = exportEndpoint.close(this);
					exportEndpoint = null;
				}
			}
			if (closed) {
				removeExportRegistration(this);
				publishEvent(
						new RemoteServiceAdminEvent(
								endpointDescription.getContainerID(),
								RemoteServiceAdminEvent.EXPORT_UNREGISTRATION,
								getBundle(), exportReference, t),
						endpointDescription);
			}
		}

		public synchronized Throwable getException() {
			return (exportEndpoint == null) ? null : exportEndpoint
					.getException();
		}

		public synchronized String toString() {
			return "ExportRegistration[exportEndpoint=" + exportEndpoint + "]";
		}

	}

	public class ExportReference implements
			org.osgi.service.remoteserviceadmin.ExportReference {

		private ServiceReference serviceReference;
		private EndpointDescription endpointDescription;

		ExportReference(ServiceReference serviceReference,
				EndpointDescription endpointDescription) {
			this.serviceReference = serviceReference;
			this.endpointDescription = endpointDescription;
		}

		public synchronized ServiceReference getExportedService() {
			return serviceReference;
		}

		public synchronized org.osgi.service.remoteserviceadmin.EndpointDescription getExportedEndpoint() {
			return endpointDescription;
		}

		synchronized void close() {
			this.serviceReference = null;
			this.endpointDescription = null;
		}

		public synchronized String toString() {
			return "ExportReference[serviceReference=" + serviceReference
					+ ", endpointDescription=" + endpointDescription + "]";
		}

	}

	public class ImportEndpoint {

		private IRemoteServiceContainerAdapter rsContainerAdapter;
		private EndpointDescription endpointDescription;
		private IRemoteServiceListener rsListener;
		private IRemoteServiceReference rsReference;
		private ServiceRegistration proxyRegistration;
		private ImportReference importReference;
		private Set<ImportRegistration> importRegistrations = new HashSet<ImportRegistration>();

		private Throwable exception;

		ImportEndpoint(IRemoteServiceContainerAdapter rsContainerAdapter,
				IRemoteServiceReference rsReference,
				IRemoteServiceListener rsListener,
				ServiceRegistration proxyRegistration,
				EndpointDescription endpointDescription) {
			this.rsContainerAdapter = rsContainerAdapter;
			this.endpointDescription = endpointDescription;
			this.rsReference = rsReference;
			this.rsListener = rsListener;
			this.proxyRegistration = proxyRegistration;
			this.importReference = new ImportReference(
					proxyRegistration.getReference(), endpointDescription);
			// Add the remoteservice listener to the container adapter, so that
			// the
			// rsListener
			// notified asynchronously if our underlying remote service
			// reference is
			// unregistered locally
			// due to disconnect or remote ejection
			this.rsContainerAdapter.addRemoteServiceListener(this.rsListener);
		}

		ImportEndpoint(IRemoteServiceContainerAdapter rsContainerAdapter,
				EndpointDescription endpointDescription, Throwable t) {
			this.rsContainerAdapter = rsContainerAdapter;
			this.endpointDescription = endpointDescription;
			this.exception = t;
		}

		synchronized EndpointDescription getEndpointDescription() {
			return endpointDescription;
		}

		synchronized Throwable getException() {
			return exception;
		}

		synchronized ID getContainerID() {
			return (rsReference == null) ? null : rsReference.getContainerID();
		}

		synchronized boolean add(ImportRegistration importRegistration) {
			return this.importRegistrations.add(importRegistration);
		}

		synchronized boolean close(ImportRegistration importRegistration) {
			boolean removed = this.importRegistrations
					.remove(importRegistration);
			if (removed && importRegistrations.size() == 0) {
				if (proxyRegistration != null) {
					proxyRegistration.unregister();
					proxyRegistration = null;
				}
				if (rsContainerAdapter != null) {
					if (rsReference != null) {
						rsContainerAdapter.ungetRemoteService(rsReference);
						rsReference = null;
					}
					// remove remote service listener
					if (rsListener != null) {
						rsContainerAdapter
								.removeRemoteServiceListener(rsListener);
						rsListener = null;
					}
					rsContainerAdapter = null;
				}
				if (importReference != null) {
					importReference.close();
					importReference = null;
				}
			}
			return removed;
		}

		synchronized ImportReference getImportReference() {
			return importReference;
		}

		synchronized boolean match(IRemoteServiceID remoteServiceID) {
			if (remoteServiceID == null || rsReference == null)
				return false;
			return rsReference.getID().equals(remoteServiceID);
		}

		public synchronized String toString() {
			return "ImportEndpoint [rsReference=" + rsReference
					+ ", proxyRegistration=" + proxyRegistration
					+ ", importReference=" + importReference + "]";
		}

		synchronized boolean match(EndpointDescription ed) {
			if (importReference == null)
				return false;
			EndpointDescription importedEndpoint = (EndpointDescription) importReference
					.getImportedEndpoint();
			if (importedEndpoint == null)
				return false;
			return importedEndpoint.isSameService(ed);
		}

	}

	public class ImportRegistration implements
			org.osgi.service.remoteserviceadmin.ImportRegistration {

		private ImportEndpoint importEndpoint;

		ImportRegistration(ImportEndpoint importEndpoint) {
			Assert.isNotNull(importEndpoint);
			this.importEndpoint = importEndpoint;
			this.importEndpoint.add(this);
		}

		synchronized ID getContainerID() {
			return importEndpoint == null ? null : importEndpoint
					.getContainerID();
		}

		synchronized EndpointDescription getEndpointDescription() {
			return (importEndpoint == null) ? null : importEndpoint
					.getEndpointDescription();
		}

		synchronized boolean match(IRemoteServiceID remoteServiceID) {
			if (importEndpoint == null)
				return false;
			return importEndpoint.match(remoteServiceID);
		}

		synchronized ImportEndpoint getImportEndpoint(EndpointDescription ed) {
			if (importEndpoint == null)
				return null;
			if (importEndpoint.match(ed))
				return importEndpoint;
			return null;
		}

		public synchronized org.osgi.service.remoteserviceadmin.ImportReference getImportReference() {
			Throwable t = getException();
			if (t != null)
				throw new IllegalStateException(
						"Cannot get import reference as registration not properly initialized",
						t);
			return importEndpoint == null ? null : importEndpoint
					.getImportReference();
		}

		public void close() {
			boolean closed = false;
			org.osgi.service.remoteserviceadmin.ImportReference importReference = null;
			EndpointDescription endpointDescription = null;
			Throwable t = null;
			synchronized (this) {
				if (importEndpoint != null) {
					t = getException();
					importReference = (t == null) ? getImportReference() : null;
					endpointDescription = getEndpointDescription();
					closed = importEndpoint.close(this);
					importEndpoint = null;
				}
			}
			if (closed) {
				removeImportRegistration(this);
				publishEvent(
						new RemoteServiceAdminEvent(
								endpointDescription.getContainerID(),
								RemoteServiceAdminEvent.IMPORT_UNREGISTRATION,
								getBundle(), importReference, t),
						endpointDescription);
			}
		}

		public synchronized Throwable getException() {
			return (importEndpoint == null) ? null : importEndpoint
					.getException();
		}

		public synchronized String toString() {
			return "ImportRegistration [importEndpoint=" + importEndpoint + "]";
		}

	}

	public class ImportReference implements
			org.osgi.service.remoteserviceadmin.ImportReference {

		private ServiceReference importedServiceReference;
		private EndpointDescription endpointDescription;

		ImportReference(ServiceReference serviceReference,
				EndpointDescription endpointDescription) {
			this.importedServiceReference = serviceReference;
			this.endpointDescription = endpointDescription;
		}

		public synchronized ServiceReference getImportedService() {
			return importedServiceReference;
		}

		public synchronized org.osgi.service.remoteserviceadmin.EndpointDescription getImportedEndpoint() {
			return endpointDescription;
		}

		synchronized void close() {
			this.importedServiceReference = null;
			this.endpointDescription = null;
		}

		public synchronized String toString() {
			return "ImportReference[importedServiceReference="
					+ importedServiceReference + ", endpointDescription="
					+ endpointDescription + "]";
		}

	}

	private void publishEvent(RemoteServiceAdminEvent event,
			EndpointDescription endpointDescription) {
		// send event synchronously to RemoteServiceAdminListeners
		EndpointPermission perm = new EndpointPermission(endpointDescription,
				Activator.getDefault().getFrameworkUUID(),
				EndpointPermission.READ);
		// notify synchronously all appropriate listeners (those with READ
		// permission)
		RemoteServiceAdminListener[] listeners = getListeners(perm);
		if (listeners != null)
			for (int i = 0; i < listeners.length; i++)
				listeners[i].remoteAdminEvent(event);

		postEvent(event, endpointDescription);
	}

	private void postEvent(RemoteServiceAdminEvent event,
			EndpointDescription endpointDescription) {
		EventAdmin eventAdmin = getEventAdmin();
		if (eventAdmin == null) {
			logWarning("postEvent", "No event admin available to post event="
					+ event + " with endpointDescription="
					+ endpointDescription);
			return;
		}
		int eventType = event.getType();
		String eventTypeName = null;
		switch (eventType) {
		case (RemoteServiceAdminEvent.EXPORT_REGISTRATION):
			eventTypeName = "EXPORT_REGISTRATION";
			break;
		case (RemoteServiceAdminEvent.EXPORT_ERROR):
			eventTypeName = "EXPORT_ERROR";
			break;
		case (RemoteServiceAdminEvent.EXPORT_UNREGISTRATION):
			eventTypeName = "EXPORT_UNREGISTRATION";
			break;
		case (RemoteServiceAdminEvent.EXPORT_WARNING):
			eventTypeName = "EXPORT_WARNING";
			break;
		case (RemoteServiceAdminEvent.IMPORT_REGISTRATION):
			eventTypeName = "IMPORT_REGISTRATION";
			break;
		case (RemoteServiceAdminEvent.IMPORT_ERROR):
			eventTypeName = "IMPORT_ERROR";
			break;
		case (RemoteServiceAdminEvent.IMPORT_UNREGISTRATION):
			eventTypeName = "IMPORT_UNREGISTRATION";
			break;
		case (RemoteServiceAdminEvent.IMPORT_WARNING):
			eventTypeName = "IMPORT_WARNING";
			break;
		}
		if (eventTypeName == null)
			logError("postEvent", "Event type=" + eventType
					+ " not understood for event=" + event + ".  Not posting");
		String topic = "org/osgi/service/remoteserviceadmin/" + eventTypeName;
		Bundle rsaBundle = getBundle();
		Dictionary eventProperties = new Properties();
		eventProperties.put("bundle", rsaBundle);
		eventProperties.put("bundle.id", new Long(rsaBundle.getBundleId()));
		eventProperties.put("bundle.symbolicname", rsaBundle.getSymbolicName());
		eventProperties.put("bundle.version", rsaBundle.getVersion());
		String[] signers = getSignersForBundle(bundle);
		if (signers != null && signers.length > 0)
			eventProperties.put("bundle.signer", signers);
		Throwable t = event.getException();
		if (t != null)
			eventProperties.put("cause", t);
		long serviceId = endpointDescription.getServiceId();
		if (serviceId != 0)
			eventProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_SERVICE_ID,
							new Long(serviceId));
		String frameworkUUID = endpointDescription.getFrameworkUUID();
		if (frameworkUUID != null)
			eventProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID,
							frameworkUUID);
		String endpointId = endpointDescription.getId();
		if (endpointId != null)
			eventProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_ID,
							endpointId);
		List<String> interfaces = endpointDescription.getInterfaces();
		if (interfaces != null && interfaces.size() > 0)
			eventProperties.put(org.osgi.framework.Constants.OBJECTCLASS,
					interfaces.toArray(new String[interfaces.size()]));
		List<String> importedConfigs = endpointDescription
				.getConfigurationTypes();
		if (importedConfigs != null && importedConfigs.size() > 0)
			eventProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_IMPORTED_CONFIGS,
							importedConfigs.toArray(new String[importedConfigs
									.size()]));
		eventProperties.put("timestamp", new Long(new Date().getTime()));
		eventProperties.put("event", event);
		postRemoteServiceAdminEvent(topic, eventProperties);

	}

	private String[] getSignersForBundle(Bundle bundle) {
		List<String> result = new ArrayList<String>();
		Map signers = bundle.getSignerCertificates(Bundle.SIGNERS_ALL);
		for (Iterator i = signers.keySet().iterator(); i.hasNext();)
			result.add(i.next().toString());
		return (String[]) result.toArray(new String[result.size()]);
	}

	private void publishExportEvent(ExportRegistration exportRegistration) {
		Throwable exception = exportRegistration.getException();
		org.osgi.service.remoteserviceadmin.ExportReference exportReference = (exception == null) ? exportRegistration
				.getExportReference() : null;
		RemoteServiceAdminEvent rsaEvent = new RemoteServiceAdminEvent(
				exportRegistration.getContainerID(),
				(exception == null) ? RemoteServiceAdminEvent.EXPORT_REGISTRATION
						: RemoteServiceAdminEvent.IMPORT_ERROR, getBundle(),
				exportReference, exception);
		publishEvent(rsaEvent, exportRegistration.getEndpointDescription());
	}

	private void publishImportEvent(ImportRegistration importRegistration) {
		Throwable exception = importRegistration.getException();
		org.osgi.service.remoteserviceadmin.ImportReference importReference = (exception == null) ? importRegistration
				.getImportReference() : null;
		RemoteServiceAdminEvent rsaEvent = new RemoteServiceAdminEvent(
				importRegistration.getContainerID(),
				(exception == null) ? RemoteServiceAdminEvent.IMPORT_REGISTRATION
						: RemoteServiceAdminEvent.IMPORT_ERROR, getBundle(),
				importReference, exception);
		publishEvent(rsaEvent, importRegistration.getEndpointDescription());
	}

	private void closeRemoteServiceAdminListenerTracker() {
		synchronized (remoteServiceAdminListenerTrackerLock) {
			if (remoteServiceAdminListenerTracker != null) {
				remoteServiceAdminListenerTracker.close();
				remoteServiceAdminListenerTracker = null;
			}
		}
	}

	private RemoteServiceAdminListener[] getListeners(EndpointPermission perm) {
		synchronized (remoteServiceAdminListenerTrackerLock) {
			if (remoteServiceAdminListenerTracker == null) {
				remoteServiceAdminListenerTracker = new ServiceTracker(
						getContext(),
						RemoteServiceAdminListener.class.getName(), null);
				remoteServiceAdminListenerTracker.open(true);
			}
		}
		ServiceReference[] unfilteredRefs = remoteServiceAdminListenerTracker
				.getServiceReferences();
		if (unfilteredRefs == null)
			return null;
		// Filter by Bundle.hasPermission
		List<ServiceReference> filteredRefs = new ArrayList<ServiceReference>();
		for (ServiceReference ref : unfilteredRefs)
			if (perm == null || ref.getBundle().hasPermission(perm))
				filteredRefs.add(ref);
		List<RemoteServiceAdminListener> results = new ArrayList<RemoteServiceAdminListener>();
		for (ServiceReference ref : filteredRefs) {
			RemoteServiceAdminListener l = (RemoteServiceAdminListener) remoteServiceAdminListenerTracker
					.getService(ref);
			if (l != null)
				results.add(l);
		}
		return results.toArray(new RemoteServiceAdminListener[results.size()]);
	}

	public Collection<org.osgi.service.remoteserviceadmin.ExportReference> getExportedServices() {
		Collection<org.osgi.service.remoteserviceadmin.ExportReference> results = new ArrayList<org.osgi.service.remoteserviceadmin.ExportReference>();
		synchronized (exportedRegistrations) {
			for (ExportRegistration reg : exportedRegistrations) {
				org.osgi.service.remoteserviceadmin.ExportReference eRef = reg.getExportReference();
				if (eRef != null) results.add(eRef);
			}
		}
		return results;
	}

	public Collection<org.osgi.service.remoteserviceadmin.ImportReference> getImportedEndpoints() {
		Collection<org.osgi.service.remoteserviceadmin.ImportReference> results = new ArrayList<org.osgi.service.remoteserviceadmin.ImportReference>();
		synchronized (importedRegistrations) {
			for (ImportRegistration reg : importedRegistrations) {
				org.osgi.service.remoteserviceadmin.ImportReference iRef = reg.getImportReference();
				if (iRef != null) results.add(iRef);
			}
		}
		return results;
	}

	// RemoteServiceAdmin service interface impl methods

	private ExportEndpoint findExistingExportEndpoint(
			ServiceReference serviceReference, ID containerID) {
		for (ExportRegistration eReg : exportedRegistrations) {
			ExportEndpoint exportEndpoint = eReg.getExportEndpoint(
					serviceReference, containerID);
			if (exportEndpoint != null)
				return exportEndpoint;
		}
		return null;
	}

	public org.osgi.service.remoteserviceadmin.ImportRegistration importService(
			org.osgi.service.remoteserviceadmin.EndpointDescription endpointDescription) {
		trace("importService", "endpointDescription=" + endpointDescription);

		if (endpointDescription.getServiceId() == 0)
			return handleNonOSGiService(endpointDescription);

		// First check to see whether it's one of ECF's endpoint descriptions
		if (endpointDescription instanceof EndpointDescription) {
			EndpointDescription ed = (EndpointDescription) endpointDescription;
			// Now get IConsumerContainerSelector, to select the ECF container
			// for the given endpointDescription
			IConsumerContainerSelector consumerContainerSelector = getConsumerContainerSelector();
			// If there is none, then we can go no further
			if (consumerContainerSelector == null) {
				logError("importService",
						"No consumerContainerSelector available");
				return null;
			}
			// Select the rsContainer to handle the endpoint description
			IRemoteServiceContainer rsContainer = consumerContainerSelector
					.selectConsumerContainer(ed);
			// If none found, log a warning and we're done
			if (rsContainer == null) {
				logWarning(
						"importService", "No remote service container selected for endpoint=" //$NON-NLS-1$
								+ endpointDescription
								+ ". Remote service NOT IMPORTED"); //$NON-NLS-1$
				return null;
			}
			// If one selected then import the service to create an import
			// registration
			ImportRegistration importRegistration = null;
			synchronized (importedRegistrations) {
				ImportEndpoint importEndpoint = findImportEndpoint(ed);
				if (importEndpoint != null)
					importRegistration = new ImportRegistration(importEndpoint);
				else {
					importEndpoint = importService(ed, rsContainer);
					importRegistration = new ImportRegistration(importEndpoint);
					if (importRegistration.getException() == null)
						importedRegistrations.add(importRegistration);
				}
			}
			// publish import event
			publishImportEvent(importRegistration);
			// Finally, return the importRegistration. It may be null or not.
			return importRegistration;
		} else {
			logWarning("importService", "endpointDescription="
					+ endpointDescription
					+ " is not ECF EndpointDescription...ignoring");
			return null;
		}
	}

	private Object eventAdminTrackerLock = new Object();
	private ServiceTracker eventAdminTracker;

	private EventAdmin getEventAdmin() {
		synchronized (eventAdminTrackerLock) {
			eventAdminTracker = new ServiceTracker(getContext(),
					EventAdmin.class.getName(), null);
			eventAdminTracker.open(true);
		}
		return (EventAdmin) eventAdminTracker.getService();
	}

	protected void postRemoteServiceAdminEvent(String topic,
			Dictionary eventProperties) {
		EventAdmin eventAdmin = getEventAdmin();
		if (eventAdmin == null) {
			logError("postRemoteServiceAdminEvent",
					"No EventAdmin service available to send eventTopic="
							+ topic + " eventProperties=" + eventProperties);
			return;
		}
		eventAdmin.postEvent(new Event(topic, eventProperties));
	}

	private void closeEventAdminTracker() {
		synchronized (eventAdminTrackerLock) {
			if (eventAdminTracker != null) {
				eventAdminTracker.close();
				eventAdminTracker = null;
			}
		}
	}

	private void closePackageAdminTracker() {
		synchronized (packageAdminTrackerLock) {
			if (packageAdminTracker != null) {
				packageAdminTracker.close();
				packageAdminTracker = null;
			}
		}
	}

	private Object consumerContainerSelectorTrackerLock = new Object();
	private ServiceTracker consumerContainerSelectorTracker;

	private void closeConsumerContainerSelectorTracker() {
		synchronized (consumerContainerSelectorTrackerLock) {
			if (consumerContainerSelectorTracker != null) {
				consumerContainerSelectorTracker.close();
				consumerContainerSelectorTracker = null;
			}
		}
		if (consumerContainerSelector != null) {
			consumerContainerSelector.close();
			consumerContainerSelector = null;
		}
	}

	private Object hostContainerSelectorTrackerLock = new Object();
	private ServiceTracker hostContainerSelectorTracker;

	private void closeHostContainerSelectorTracker() {
		synchronized (hostContainerSelectorTrackerLock) {
			if (hostContainerSelectorTracker != null) {
				hostContainerSelectorTracker.close();
				hostContainerSelectorTracker = null;
			}
		}
		if (hostContainerSelector != null) {
			hostContainerSelector.close();
			hostContainerSelector = null;
		}
	}

	protected IHostContainerSelector getHostContainerSelector() {
		synchronized (hostContainerSelectorTrackerLock) {
			if (hostContainerSelectorTracker == null) {
				hostContainerSelectorTracker = new ServiceTracker(getContext(),
						IHostContainerSelector.class.getName(), null);
				hostContainerSelectorTracker.open();
			}
		}
		return (IHostContainerSelector) hostContainerSelectorTracker
				.getService();
	}

	protected IConsumerContainerSelector getConsumerContainerSelector() {
		synchronized (consumerContainerSelectorTrackerLock) {
			if (consumerContainerSelectorTracker == null) {
				consumerContainerSelectorTracker = new ServiceTracker(
						getContext(),
						IConsumerContainerSelector.class.getName(), null);
				consumerContainerSelectorTracker.open();
			}
		}
		return (IConsumerContainerSelector) consumerContainerSelectorTracker
				.getService();
	}

	private Version getPackageVersion(ServiceReference serviceReference, String serviceInterface, String packageName) {
		Object service = getContext().getService(serviceReference);
		if (service == null) return null;
		Class[] interfaceClasses = service.getClass().getInterfaces();
		if (interfaceClasses == null) return null;
		Class interfaceClass = null;
		for(int i=0; i < interfaceClasses.length; i++) {
			if (interfaceClasses[i].getName().equals(serviceInterface)) interfaceClass = interfaceClasses[i];
		}
		if (interfaceClass == null) return null;
		ExportedPackage exportedPackage = getExportedPackageForClass(getPackageAdmin(), interfaceClass);
		return (exportedPackage == null)?null:exportedPackage.getVersion();
	}

	protected Map<String, Object> createExportEndpointDescriptionProperties(
			ServiceReference serviceReference,
			Map<String, Object> overridingProperties,
			String[] exportedInterfaces, String[] serviceIntents,
			IRemoteServiceContainer rsContainer) {
		IContainer container = rsContainer.getContainer();
		ID containerID = container.getID();

		Map<String, Object> endpointDescriptionProperties = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);

		// OSGi properties
		// OBJECTCLASS set to exportedInterfaces
		endpointDescriptionProperties.put(
				org.osgi.framework.Constants.OBJECTCLASS, exportedInterfaces);

		// Service interface versions
		for (int i = 0; i < exportedInterfaces.length; i++) {
			String packageName = getPackageName(exportedInterfaces[i]);
			String packageVersionKey = org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_PACKAGE_VERSION_
					+ packageName;
			// If it's pre-set...by registration or by overridingProperties,
			// then use that value
			String packageVersion = (String) PropertiesUtil.getPropertyValue(
					serviceReference, overridingProperties, packageVersionKey);
			if (packageVersion == null) {
				Version version = getPackageVersion(serviceReference, exportedInterfaces[i], packageName);
				if (version != null && !version.equals(Version.emptyVersion))
					packageVersion = version.toString();
				else
					logWarning("createExportEndpointDescription",
							"No or empty version specified for exported service interface="
									+ exportedInterfaces[i]);
			}
			// Only set the package version if we have a non-null value
			if (packageVersion != null)
				endpointDescriptionProperties.put(packageVersionKey,
						packageVersion);
		}

		// ENDPOINT_ID
		String endpointId = (String) PropertiesUtil
				.getPropertyValue(
						serviceReference,
						overridingProperties,
						org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_ID);
		if (endpointId == null)
			endpointId = containerID.getName();
		endpointDescriptionProperties
				.put(org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_ID,
						endpointId);

		// ENDPOINT_SERVICE_ID
		// This is always set to the value from serviceReference as per 122.5.1
		Long serviceId = (Long) serviceReference
				.getProperty(org.osgi.framework.Constants.SERVICE_ID);
		endpointDescriptionProperties.put(
				org.osgi.framework.Constants.SERVICE_ID, serviceId);

		// ENDPOINT_FRAMEWORK_ID
		String frameworkId = (String) PropertiesUtil
				.getPropertyValue(
						serviceReference,
						overridingProperties,
						org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID);
		if (frameworkId == null)
			frameworkId = Activator.getDefault().getFrameworkUUID();
		endpointDescriptionProperties
				.put(org.osgi.service.remoteserviceadmin.RemoteConstants.ENDPOINT_FRAMEWORK_UUID,
						frameworkId);

		// REMOTE_CONFIGS_SUPPORTED
		String[] remoteConfigsSupported = getSupportedConfigs(container);
		if (remoteConfigsSupported != null)
			endpointDescriptionProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.REMOTE_CONFIGS_SUPPORTED,
							remoteConfigsSupported);
		// SERVICE_IMPORTED_CONFIGS...set to same value as remote configs
		// supported (which is computed
		// for the exporting ECF container
		endpointDescriptionProperties
				.put(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_IMPORTED_CONFIGS,
						remoteConfigsSupported);

		// SERVICE_INTENTS
		String[] intents = (String[]) PropertiesUtil
				.getPropertyValue(
						null,
						overridingProperties,
						org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_INTENTS);
		if (intents == null)
			intents = serviceIntents;
		if (intents != null)
			endpointDescriptionProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_INTENTS,
							intents);

		// REMOTE_INTENTS_SUPPORTED
		String[] remoteIntentsSupported = getSupportedIntents(container);
		if (remoteIntentsSupported != null)
			endpointDescriptionProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.REMOTE_INTENTS_SUPPORTED,
							remoteIntentsSupported);

		// ECF properties
		// ID namespace
		String idNamespace = containerID.getNamespace().getName();
		endpointDescriptionProperties.put(
				RemoteConstants.ENDPOINT_CONTAINER_ID_NAMESPACE, idNamespace);

		// ENDPOINT_CONNECTTARGET_ID
		String connectTarget = (String) PropertiesUtil.getPropertyValue(
				serviceReference, overridingProperties,
				RemoteConstants.ENDPOINT_CONNECTTARGET_ID);
		if (connectTarget == null) {
			ID connectedID = rsContainer.getContainer().getConnectedID();
			if (connectedID != null && !connectedID.equals(containerID))
				connectTarget = connectedID.getName();
		}
		if (connectTarget != null)
			endpointDescriptionProperties.put(
					RemoteConstants.ENDPOINT_CONNECTTARGET_ID, connectTarget);

		// ENDPOINT_IDFILTER_IDS
		String[] idFilter = (String[]) PropertiesUtil.getPropertyValue(
				serviceReference, overridingProperties,
				RemoteConstants.ENDPOINT_IDFILTER_IDS);
		if (idFilter != null && idFilter.length > 0)
			endpointDescriptionProperties.put(
					RemoteConstants.ENDPOINT_IDFILTER_IDS, idFilter);

		// ENDPOINT_REMOTESERVICE_FILTER
		String rsFilter = (String) PropertiesUtil.getPropertyValue(
				serviceReference, overridingProperties,
				RemoteConstants.ENDPOINT_REMOTESERVICE_FILTER);
		if (rsFilter != null)
			endpointDescriptionProperties.put(
					RemoteConstants.ENDPOINT_REMOTESERVICE_FILTER, rsFilter);

		return endpointDescriptionProperties;

	}

	protected Map<String, Object> copyNonReservedProperties(
			ServiceReference serviceReference,
			Map<String, Object> overridingProperties, Map<String, Object> target) {
		// copy all other properties...from service reference
		PropertiesUtil.copyNonReservedProperties(serviceReference, target);
		// And override with overridingProperties
		PropertiesUtil.copyNonReservedProperties(overridingProperties, target);
		return target;
	}

	protected ContainerTypeDescription getContainerTypeDescription(
			IContainer container) {
		IContainerManager containerManager = Activator.getDefault()
				.getContainerManager();
		if (containerManager == null)
			return null;
		return containerManager.getContainerTypeDescription(container.getID());
	}

	private String[] getSupportedConfigs(IContainer container) {
		ContainerTypeDescription ctd = getContainerTypeDescription(container);
		return (ctd == null) ? null : ctd.getSupportedConfigs();
	}

	private String[] getImportedConfigs(IContainer container,
			String[] exporterSupportedConfigs) {
		ContainerTypeDescription ctd = getContainerTypeDescription(container);
		return (ctd == null) ? null : ctd
				.getImportedConfigs(exporterSupportedConfigs);
	}

	private String[] getSupportedIntents(IContainer container) {
		ContainerTypeDescription ctd = getContainerTypeDescription(container);
		return (ctd == null) ? null : ctd.getSupportedIntents();
	}

	protected ID[] getIDFilter(EndpointDescription endpointDescription,
			ID endpointID) {
		ID[] idFilter = endpointDescription.getIDFilter();
		// If it is null,
		return (idFilter == null) ? new ID[] { endpointID } : idFilter;
	}

	protected String getRemoteServiceFilter(
			EndpointDescription endpointDescription) {
		long rsId = endpointDescription.getRemoteServiceId();
		if (rsId == 0) {
			// It's not known...so we just return the 'raw' remote service
			// filter
			return endpointDescription.getRemoteServiceFilter();
		} else {
			String edRsFilter = endpointDescription.getRemoteServiceFilter();
			// It's a real remote service id...so we return
			StringBuffer result = new StringBuffer("(&(") //$NON-NLS-1$
					.append(org.eclipse.ecf.remoteservice.Constants.SERVICE_ID)
					.append("=").append(rsId).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
			if (edRsFilter != null)
				result.append(edRsFilter);
			result.append(")"); //$NON-NLS-1$
			return result.toString();
		}
	}

	protected org.osgi.service.remoteserviceadmin.ImportRegistration handleNonOSGiService(
			org.osgi.service.remoteserviceadmin.EndpointDescription endpointDescription) {
		// With non-OSGi service id (service id=0), we log a warning and return
		// null;
		logWarning("handleNonOSGiService",
				"OSGi remote service id is 0 for endpointDescription="
						+ endpointDescription);
		return null;
	}

	protected ImportEndpoint createAndRegisterProxy(
			EndpointDescription endpointDescription,
			IRemoteServiceContainer rsContainer,
			IRemoteServiceReference selectedRsReference) throws Exception {

		IRemoteServiceContainerAdapter containerAdapter = rsContainer
				.getContainerAdapter();
		ID rsContainerID = rsContainer.getContainer().getID();
		// First get IRemoteService for selectedRsReference
		IRemoteService rs = containerAdapter
				.getRemoteService(selectedRsReference);
		if (rs == null)
			throw new NullPointerException(
					"getRemoteService returned null for selectedRsReference="
							+ selectedRsReference + ",rsContainerID="
							+ rsContainerID);

		Map proxyProperties = createProxyProperties(endpointDescription,
				rsContainer, selectedRsReference, rs);

		List<String> interfaces = endpointDescription.getInterfaces();

		IRemoteServiceListener rsListener = createRemoteServiceListener();

		ServiceRegistration proxyRegistration = getContext().registerService(
				(String[]) interfaces.toArray(new String[interfaces.size()]),
				createProxyServiceFactory(endpointDescription, rs),
				(Dictionary) PropertiesUtil
						.createDictionaryFromMap(proxyProperties));

		return new ImportEndpoint(containerAdapter, selectedRsReference,
				rsListener, proxyRegistration, endpointDescription);
	}

	protected ServiceFactory createProxyServiceFactory(
			EndpointDescription endpointDescription,
			IRemoteService remoteService) {
		return new ProxyServiceFactory(
				endpointDescription.getInterfaceVersions(), remoteService);
	}

	protected Collection<Class> loadServiceInterfacesViaBundle(Bundle bundle,
			String[] interfaces) {
		List<Class> result = new ArrayList<Class>();
		for (int i = 0; i < interfaces.length; i++) {
			try {
				result.add(bundle.loadClass(interfaces[i]));
			} catch (ClassNotFoundException e) {
				logError("loadInterfacesViaBundle", "interface="
						+ interfaces[i] + " cannot be loaded by bundle="
						+ bundle.getSymbolicName(), e);
				continue;
			} catch (IllegalStateException e) {
				logError("loadInterfacesViaBundle", "interface="
						+ interfaces[i]
						+ " cannot be loaded since bundle is in illegal state",
						e);
				continue;
			}
		}
		return result;
	}

	protected class ProxyServiceFactory implements ServiceFactory {
		private IRemoteService remoteService;
		private Map<String, Version> interfaceVersions;

		public ProxyServiceFactory(Map<String, Version> interfaceVersions,
				IRemoteService remoteService) {
			this.interfaceVersions = interfaceVersions;
			this.remoteService = remoteService;
		}

		public Object getService(Bundle bundle, ServiceRegistration registration) {
			return createProxy(bundle, registration.getReference(),
					remoteService, interfaceVersions);
		}

		public void ungetService(Bundle bundle,
				ServiceRegistration registration, Object service) {
			ungetProxyClassLoader(bundle);
			this.remoteService = null;
			this.interfaceVersions = null;
		}
	}

	protected Object createProxy(Bundle requestingBundle,
			ServiceReference serviceReference, IRemoteService remoteService,
			Map<String, Version> interfaceVersions) {
		// Get symbolicName once for possible use below
		String bundleSymbolicName = requestingBundle.getSymbolicName();
		// Get String[] via OBJECTCLASS constant property
		String[] serviceClassnames = (String[]) serviceReference
				.getProperty(org.osgi.framework.Constants.OBJECTCLASS);
		// Load as many of the serviceInterface classes as possible
		Collection<Class> serviceInterfaceClasses = loadServiceInterfacesViaBundle(
				requestingBundle, serviceClassnames);
		// There has to be at least one serviceInterface that the bundle can
		// load...otherwise the service can't be accessed
		if (serviceInterfaceClasses.size() < 1)
			throw new RuntimeException(
					"ProxyServiceFactory cannot load any serviceInterfaces="
							+ serviceInterfaceClasses
							+ " for serviceReference=" + serviceReference
							+ " via bundle=" + bundleSymbolicName);

		// Now verify that the classes are of valid versions
		verifyServiceInterfaceVersionsForProxy(requestingBundle,
				serviceInterfaceClasses, interfaceVersions);

		// Now create/get class loader for proxy. This will typically
		// be an instance of ProxyClassLoader
		ClassLoader cl = getProxyClassLoader(requestingBundle);
		try {
			return remoteService.getProxy(cl, (Class[]) serviceInterfaceClasses
					.toArray(new Class[serviceInterfaceClasses.size()]));
		} catch (ECFException e) {
			throw new ServiceException(
					"ProxyServiceFactory cannot create proxy for bundle="
							+ bundleSymbolicName + " from serviceReference="
							+ serviceReference, e);
		}

	}

	private Map<Bundle, ProxyClassLoader> proxyClassLoaders = new HashMap<Bundle, ProxyClassLoader>();

	private void closeProxyClassLoaderCache() {
		synchronized (proxyClassLoaders) {
			proxyClassLoaders.clear();
		}
	}

	protected ClassLoader getProxyClassLoader(Bundle bundle) {
		ProxyClassLoader proxyClassLoaderForBundle = null;
		synchronized (proxyClassLoaders) {
			proxyClassLoaderForBundle = proxyClassLoaders.get(bundle);
			if (proxyClassLoaderForBundle == null) {
				proxyClassLoaderForBundle = new ProxyClassLoader(bundle);
				proxyClassLoaders.put(bundle, proxyClassLoaderForBundle);
			} else
				proxyClassLoaderForBundle.addServiceUseCount();
		}
		return proxyClassLoaderForBundle;
	}

	protected void ungetProxyClassLoader(Bundle bundle) {
		synchronized (proxyClassLoaders) {
			ProxyClassLoader proxyClassLoaderForBundle = proxyClassLoaders
					.get(bundle);
			if (proxyClassLoaderForBundle != null) {
				int useCount = proxyClassLoaderForBundle.getServiceUseCount();
				if (useCount == 0)
					proxyClassLoaders.remove(bundle);
				else
					proxyClassLoaderForBundle.removeServiceUseCount();
			}
		}
	}

	protected class ProxyClassLoader extends ClassLoader {
		private Bundle loadingBundle;
		private int serviceUseCount = 0;

		public ProxyClassLoader(Bundle loadingBundle) {
			this.loadingBundle = loadingBundle;
		}

		public Class loadClass(String name) throws ClassNotFoundException {
			return loadingBundle.loadClass(name);
		}

		public int getServiceUseCount() {
			return serviceUseCount;
		}

		public void addServiceUseCount() {
			serviceUseCount++;
		}

		public void removeServiceUseCount() {
			serviceUseCount--;
		}
	}

	private PackageAdmin getPackageAdmin() {
		synchronized (packageAdminTrackerLock) {
			if (packageAdminTracker == null) {
				packageAdminTracker = new ServiceTracker(getContext(),
						PackageAdmin.class.getName(), null);
				packageAdminTracker.open();
			}
		}
		return (PackageAdmin) packageAdminTracker.getService();
	}

	protected IPackageVersionComparator getPackageVersionComparator() {
		return packageVersionComparator;
	}

	private ExportedPackage getExportedPackageForClass(
			PackageAdmin packageAdmin, Class clazz) {
		String packageName = getPackageName(clazz.getName());
		// Get all exported packages with given package name
		ExportedPackage[] exportedPackagesWithName = packageAdmin
				.getExportedPackages(packageName);
		// If none then we return null
		if (exportedPackagesWithName == null)
			return null;
		// Get the bundle for the previously loaded interface class
		Bundle classBundle = packageAdmin.getBundle(clazz);
		if (classBundle == null)
			return null;
		for (int i = 0; i < exportedPackagesWithName.length; i++) {
			Bundle packageBundle = exportedPackagesWithName[i]
					.getExportingBundle();
			if (packageBundle == null)
				continue;
			if (packageBundle.equals(classBundle))
				return exportedPackagesWithName[i];
		}
		return null;
	}

	private String getPackageName(String className) {
		int lastDotIndex = className.lastIndexOf(".");
		if (lastDotIndex == -1)
			return "";
		return className.substring(0, lastDotIndex);
	}

	protected void verifyServiceInterfaceVersionsForProxy(Bundle bundle,
			Collection<Class> classes, Map<String, Version> interfaceVersions) {
		IPackageVersionComparator packageVersionComparator = getPackageVersionComparator();
		if (packageVersionComparator == null) {
			logError(
					"verifyServiceInterfaceVersionsForProxy",
					"No package version comparator available, skipping package version comparison for service classes="
							+ classes);
			return;
		}
		// For all service interface classes
		for (Class clazz : classes) {
			String className = clazz.getName();
			String packageName = getPackageName(className);
			ExportedPackage exportedPackage = getExportedPackageForClass(
					getPackageAdmin(), clazz);
			if (exportedPackage == null)
				throw new NullPointerException(
						"No exported package found for class=" + className);
			// Now lookup version from specification
			Version remotePackageVersion = interfaceVersions.get(className);
			if (remotePackageVersion == null)
				throw new NullPointerException("Remote package=" + packageName
						+ " has no Version");
			Version localPackageVersion = exportedPackage.getVersion();
			if (localPackageVersion == null)
				throw new NullPointerException("Local package=" + packageName
						+ " has no Version");

			// Now do compare via package version comparator service
			packageVersionComparator.comparePackageVersions(packageName,
					remotePackageVersion, localPackageVersion);
		}
	}

	protected IRemoteServiceReference selectRemoteServiceReference(
			Collection<IRemoteServiceReference> rsRefs, ID targetID,
			ID[] idFilter, Collection<String> interfaces, String rsFilter,
			IRemoteServiceContainer rsContainer) {
		if (rsRefs.size() == 0)
			return null;
		if (rsRefs.size() > 1) {
			logWarning("selectRemoteServiceReference", "rsRefs=" + rsRefs
					+ ",targetID=" + targetID + ",idFilter=" + idFilter
					+ ",interfaces=" + interfaces + ",rsFilter=" + rsFilter
					+ ",rsContainer=" + rsContainer.getContainer().getID()
					+ " has " + rsRefs.size()
					+ " values.  Selecting the first element");
		}
		return rsRefs.iterator().next();
	}

	protected Map createProxyProperties(
			EndpointDescription endpointDescription,
			IRemoteServiceContainer rsContainer,
			IRemoteServiceReference rsReference, IRemoteService remoteService) {

		Map resultProperties = new TreeMap<String, Object>(
				String.CASE_INSENSITIVE_ORDER);
		PropertiesUtil.copyNonReservedProperties(rsReference, resultProperties);
		// remove OBJECTCLASS
		resultProperties
				.remove(org.eclipse.ecf.remoteservice.Constants.OBJECTCLASS);
		// remove remote service id
		resultProperties
				.remove(org.eclipse.ecf.remoteservice.Constants.SERVICE_ID);
		// Set intents if there are intents
		Object intentsValue = PropertiesUtil
				.convertToStringPlusValue(endpointDescription.getIntents());
		if (intentsValue != null)
			resultProperties
					.put(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_INTENTS,
							intentsValue);
		// Set service.imported to IRemoteService
		resultProperties
				.put(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_IMPORTED,
						remoteService);

		String[] exporterSupportedConfigs = (String[]) endpointDescription
				.getProperties()
				.get(org.osgi.service.remoteserviceadmin.RemoteConstants.REMOTE_CONFIGS_SUPPORTED);
		String[] importedConfigs = getImportedConfigs(
				rsContainer.getContainer(), exporterSupportedConfigs);
		// Set service.imported.configs
		resultProperties
				.put(org.osgi.service.remoteserviceadmin.RemoteConstants.SERVICE_IMPORTED_CONFIGS,
						importedConfigs);
		return resultProperties;
	}

	protected ExportEndpoint exportService(ServiceReference serviceReference,
			Map<String, Object> overridingProperties,
			String[] exportedInterfaces, String[] serviceIntents,
			IRemoteServiceContainer rsContainer) {

		Map endpointDescriptionProperties = createExportEndpointDescriptionProperties(
				serviceReference, overridingProperties, exportedInterfaces,
				serviceIntents, rsContainer);
		// Create remote service properties
		Map remoteServiceProperties = copyNonReservedProperties(
				serviceReference, overridingProperties,
				new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER));

		IRemoteServiceContainerAdapter containerAdapter = rsContainer
				.getContainerAdapter();
		// Register remote service via ECF container adapter to create
		// remote service registration
		IRemoteServiceRegistration remoteRegistration = null;
		Throwable exception = null;
		try {
			if (containerAdapter instanceof IOSGiRemoteServiceContainerAdapter) {
				IOSGiRemoteServiceContainerAdapter osgiContainerAdapter = (IOSGiRemoteServiceContainerAdapter) containerAdapter;
				remoteRegistration = osgiContainerAdapter
						.registerRemoteService(
								exportedInterfaces,
								serviceReference,
								PropertiesUtil
										.createDictionaryFromMap(remoteServiceProperties));
			} else
				remoteRegistration = containerAdapter
						.registerRemoteService(
								exportedInterfaces,
								getContext().getService(serviceReference),
								PropertiesUtil
										.createDictionaryFromMap(remoteServiceProperties));
			endpointDescriptionProperties
					.put(org.eclipse.ecf.remoteservice.Constants.SERVICE_ID,
							remoteRegistration
									.getProperty(org.eclipse.ecf.remoteservice.Constants.SERVICE_ID));
		} catch (Exception e) {
			exception = e;
			if (remoteRegistration != null)
				remoteRegistration.unregister();
		}
		EndpointDescription endpointDescription = new EndpointDescription(
				serviceReference, endpointDescriptionProperties);
		// Create ExportEndpoint/ExportRegistration
		return (exception == null) ? new ExportEndpoint(serviceReference,
				endpointDescription, remoteRegistration) : new ExportEndpoint(
				serviceReference, endpointDescription, exception);
	}

	protected ImportEndpoint importService(
			EndpointDescription endpointDescription,
			IRemoteServiceContainer rsContainer) {
		trace("doImportService", "endpointDescription=" + endpointDescription
				+ ",rsContainerID=" + rsContainer.getContainer().getID());
		// Get interfaces from endpoint description
		Collection<String> interfaces = endpointDescription.getInterfaces();
		Assert.isNotNull(interfaces);
		Assert.isTrue(interfaces.size() > 0);
		// Get ECF endpoint ID...if this throws IDCreateException (because the
		// local system does not have
		// namespace for creating ID, or no namespace is present in
		// endpointDescription or endpoint id,
		// then it will be caught by the caller
		ID endpointContainerID = endpointDescription.getContainerID();
		Assert.isNotNull(endpointContainerID);
		// Get connect target ID. May be null
		ID targetID = endpointDescription.getConnectTargetID();
		if (targetID == null)
			targetID = endpointContainerID;
		// Get idFilter...also may be null
		ID[] idFilter = getIDFilter(endpointDescription, endpointContainerID);
		// Get remote service filter
		String rsFilter = getRemoteServiceFilter(endpointDescription);
		// IRemoteServiceReferences from query
		Collection<IRemoteServiceReference> rsRefs = new ArrayList<IRemoteServiceReference>();
		// Get IRemoteServiceContainerAdapter
		IRemoteServiceContainerAdapter containerAdapter = rsContainer
				.getContainerAdapter();
		// rsContainerID
		ID rsContainerID = rsContainer.getContainer().getID();
		try {
			// For all given interfaces
			for (String intf : interfaces) {
				// Get/lookup remote service references
				IRemoteServiceReference[] refs = containerAdapter
						.getRemoteServiceReferences(targetID, idFilter, intf,
								rsFilter);
				if (refs == null) {
					logWarning("doImportService",
							"getRemoteServiceReferences return null for targetID="
									+ targetID + ",idFilter=" + idFilter
									+ ",intf=" + intf + ",rsFilter=" + rsFilter
									+ " on rsContainerID=" + rsContainerID);
					continue;
				}
				for (int i = 0; i < refs.length; i++)
					rsRefs.add(refs[i]);
			}
			IRemoteServiceReference selectedRsReference = selectRemoteServiceReference(
					rsRefs, targetID, idFilter, interfaces, rsFilter,
					rsContainer);

			if (selectedRsReference == null) {
				logWarning("doImportService",
						"selectRemoteServiceReference returned null for rsRefs="
								+ rsRefs + ",targetID=" + targetID
								+ ",idFilter=" + idFilter + ",interfaces="
								+ interfaces + ",rsFilter=" + rsFilter
								+ ",rsContainerID=" + rsContainerID);
				return null;
			}

			return createAndRegisterProxy(endpointDescription, rsContainer,
					selectedRsReference);
		} catch (Exception e) {
			return new ImportEndpoint(rsContainer.getContainerAdapter(),
					endpointDescription, e);
		}
	}

	protected IRemoteServiceReference[] doGetRemoteServiceReferences(
			IRemoteServiceContainerAdapter containerAdapter, ID targetID,
			ID[] idFilter, String intf, String rsFilter)
			throws ContainerConnectException, InvalidSyntaxException {
		trace("doGetRemoteServiceReferences",
				"getRemoteServiceReferences targetID=" + targetID
						+ ",idFilter=" + Arrays.asList(idFilter) + ",intf="
						+ intf + ",rsFilter=" + rsFilter);
		return containerAdapter.getRemoteServiceReferences(targetID, idFilter,
				intf, rsFilter);
	}

	private void closeExportRegistrations() {
		List<ExportRegistration> toClose = null;
		synchronized (exportedRegistrations) {
			toClose = new ArrayList<ExportRegistration>(exportedRegistrations);
			exportedRegistrations.clear();
		}
		for(ExportRegistration reg: toClose) reg.close();
	}
	
	private void closeImportRegistrations() {
		List<ImportRegistration> toClose = null;
		synchronized (importedRegistrations) {
			toClose = new ArrayList<ImportRegistration>(importedRegistrations);
			importedRegistrations.clear();
		}
		for(ImportRegistration reg: toClose) reg.close();
	}

	public void close() {
		trace("close","closing importedRegistrations="+importedRegistrations+" exportedRegistrations="+exportedRegistrations);
		closeRemoteServiceAdminListenerTracker();
		closeEventAdminTracker();
		closePackageAdminTracker();
		closeProxyClassLoaderCache();
		closeConsumerContainerSelectorTracker();
		closeHostContainerSelectorTracker();
		closeDefaultContainerSelectors();
		closeImportRegistrations();
		closeExportRegistrations();
		this.bundle = null;
	}

	private ImportEndpoint findImportEndpoint(EndpointDescription ed) {
		for (ImportRegistration reg : importedRegistrations) {
			ImportEndpoint endpoint = reg.getImportEndpoint(ed);
			if (endpoint != null)
				return endpoint;
		}
		return null;
	}

	protected void unimportService(IRemoteServiceID remoteServiceID) {
		List<ImportRegistration> removedRegistrations = new ArrayList<ImportRegistration>();
		synchronized (importedRegistrations) {
			for (Iterator<ImportRegistration> i = importedRegistrations
					.iterator(); i.hasNext();) {
				ImportRegistration importRegistration = i.next();
				if (importRegistration != null
						&& importRegistration.match(remoteServiceID))
					removedRegistrations.add(importRegistration);
			}
		}
		// Now close all of them
		for (ImportRegistration removedReg : removedRegistrations) {
			trace("unimportService", "closing importRegistration=" + removedReg);
			removedReg.close();
		}
	}

	protected class RemoteServiceListener implements IRemoteServiceListener {
		public void handleServiceEvent(IRemoteServiceEvent event) {
			if (event instanceof IRemoteServiceUnregisteredEvent)
				unimportService(event.getReference().getID());
		}
	}

	protected IRemoteServiceListener createRemoteServiceListener() {
		return new RemoteServiceListener();
	}

	protected void logError(String methodName, String message, IStatus status) {
		LogUtility.logError(methodName, DebugOptions.REMOTE_SERVICE_ADMIN,
				this.getClass(), status);
	}

	protected void trace(String methodName, String message) {
		LogUtility.trace(methodName, DebugOptions.REMOTE_SERVICE_ADMIN,
				this.getClass(), message);
	}

	protected void logWarning(String methodName, String message) {
		LogUtility.logWarning(methodName, DebugOptions.REMOTE_SERVICE_ADMIN,
				this.getClass(), message);
	}

	protected void logError(String methodName, String message, Throwable t) {
		LogUtility.logError(methodName, DebugOptions.REMOTE_SERVICE_ADMIN,
				this.getClass(), message, t);
	}

	protected void logError(String methodName, String message) {
		logError(methodName, message, (Throwable) null);
	}

}