package org.eclipse.ecf.ui.wizards;

import org.eclipse.ecf.core.ContainerCreateException;
import org.eclipse.ecf.core.ContainerFactory;
import org.eclipse.ecf.core.ContainerTypeDescription;
import org.eclipse.ecf.core.IContainer;
import org.eclipse.ecf.ui.IConfigurationWizard;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;

public class EmptyConfigurationWizard extends Wizard implements
		IConfigurationWizard {

	protected IContainer container;
	
	protected IWizardPage mainPage;
	
	protected ContainerTypeDescription containerDescription;
	
	protected Object [] containerParameters = null;
	
	protected ContainerTypeDescription getContainerTypeDescription() {
		return containerDescription;
	}
	
	public boolean performFinish() {
		try {
			container = ContainerFactory.getDefault()
			.createContainer(containerDescription, containerParameters);
			return true;
		} catch (ContainerCreateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public IContainer getConfigurationResult() {
		return container;
	}

	public void init(IWorkbench workbench,
			ContainerTypeDescription containerDescription) {
		this.containerDescription = containerDescription;
	}

	public void addPages() {
		mainPage = new WizardPage("mainPage") {
			public void createControl(Composite parent) {
				setPageComplete(true);
				setControl(parent);
			}
		};
		addPage(mainPage);
	}
}
