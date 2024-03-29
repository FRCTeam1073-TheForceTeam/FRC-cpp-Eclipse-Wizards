package subsystemwizard.wizards;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.operation.*;
import java.lang.reflect.InvocationTargetException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import java.io.*;

import org.eclipse.ui.*;
import org.eclipse.ui.ide.IDE;

/**
 * This is a sample new wizard. Its role is to create a new file 
 * resource in the provided container. If the container resource
 * (a folder or a project) is selected in the workspace 
 * when the wizard is opened, it will accept it as the target
 * container. The wizard creates one file with the extension
 * "mpe". If a sample multi-page editor (also available
 * as a template) is registered for the same extension, it will
 * be able to open it.
 */

public class SubsystemWizard extends Wizard implements INewWizard {
	private SubsystemWizardPage page;
	private ISelection selection;

	/**
	 * Constructor for Subsystem Wizard.
	 */
	public SubsystemWizard() {
		super();
		setNeedsProgressMonitor(true);
	}
	
	/**
	 * Adding the page to the wizard.
	 */

	public void addPages() {
		page = new SubsystemWizardPage(selection);
		addPage(page);
	}

	/**
	 * This method is called when 'Finish' button is pressed in
	 * the wizard. We will create an operation and run it
	 * using wizard as execution context.
	 */
	public boolean performFinish() {
		final String containerName = page.getContainerName();
		final String fileName = page.getFileName();
		final boolean implementation = page.doImplementation();
		final boolean nonOverideable = page.nonOverideable();
		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				try {
					doFinish(containerName, fileName, 
							implementation, nonOverideable, monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		};
		try {
			getContainer().run(true, false, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			Throwable realException = e.getTargetException();
			MessageDialog.openError(getShell(), "Error", realException.getMessage());
			return false;
		}
		return true;
	}
	
	/**
	 * The worker method. It will find the container, create the
	 * file if missing or just replace its contents, and open
	 * the editor on the newly created file.
	 */

	private void doFinish(
		String containerName,
		String fileName,
		boolean implementation,
		boolean nonOverideable,
		IProgressMonitor monitor)
		throws CoreException {
		// create a sample file
		
		monitor.beginTask("Creating " + fileName, 2);
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IResource resource = root.findMember(new Path(containerName));
		if (!resource.exists() || !(resource instanceof IContainer)) {
			throwCoreException("Container \"" + containerName + "\" does not exist.");
		}
		IContainer container = (IContainer) resource;
		final IFile file = container.getFile(new Path(fileName + ".h"));
		final IFile implementationFile = container.getFile(new Path(fileName + ".cpp"));
		try {
			InputStream stream = openHeaderStream(fileName, nonOverideable);
			if (file.exists())	file.setContents(stream, true, true, monitor);
			else file.create(stream, true, monitor);
			if(implementation){
				stream = openImplementationStream(fileName);
				if(implementationFile.exists()) implementationFile.setContents(stream, true, true, monitor);
				else implementationFile.create(stream, true, monitor);
			}
			stream.close();
		} catch (IOException e) {}
		
		monitor.worked(1);
		monitor.setTaskName("Opening file for editing...");
		getShell().getDisplay().asyncExec(new Runnable() {
			public void run() {
				IWorkbenchPage page =
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				try {
					IDE.openEditor(page, file, true);
				} catch (PartInitException e) {
				}
			}
		});
		if(implementation){
			getShell().getDisplay().asyncExec(new Runnable(){
				public void run() {
					IWorkbenchPage page = 
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					try{
						IDE.openEditor(page, implementationFile, true);
					} catch (PartInitException e){}
				}
			});
		}
	
		monitor.worked(1);
	}
	
	/**
	 * We will initialize file contents with a sample text.
	 * @throws CoreException 
	 */

	private InputStream openHeaderStream(String title, boolean nonOverrideable) throws CoreException {
		final String TITLE = title.toUpperCase();
		final String virtual = nonOverrideable ? "" : "virtual ";
		final InputStream input = getClass().getResourceAsStream("SubsystemTemplate");
		final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
		String line;
		StringBuffer outputBuffer = new StringBuffer();
		try {
			try{
				while((line = reader.readLine()) != null){
					line = line.replaceAll(generateReplacement(title), title);
					line = line.replaceFirst(generateReplacement(TITLE), TITLE);
					line = line.replaceAll(generateReplacement(virtual), virtual);
					outputBuffer.append(line);
					outputBuffer.append("\n");
				}
			}
			finally{
				reader.close();
			}
		} 
		catch (IOException e) {
			IStatus status = new Status(IStatus.ERROR, "ExampleWizard", IStatus.OK,
					e.getLocalizedMessage(), null);
            throw new CoreException(status);
		}
		return new ByteArrayInputStream(outputBuffer.toString().getBytes());
	}
	private InputStream openImplementationStream(String title) throws CoreException{
		StringBuffer outputBuffer = new StringBuffer();
		String line;
		final BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("SubsystemImplementationTemplate")));
		try{
			try{
				while((line = reader.readLine()) != null){
					line = line.replaceAll(generateReplacement(title), title);
					outputBuffer.append(line);
					outputBuffer.append("\n");
				}
			}
			finally{
				reader.close();
			}
		}
		catch(IOException e){
			IStatus status = new Status(IStatus.ERROR, "ExampleWizard", IStatus.OK,
					e.getLocalizedMessage(), null);
            throw new CoreException(status);
			
		}
		return new ByteArrayInputStream(outputBuffer.toString().getBytes());
	}

	private void throwCoreException(String message) throws CoreException {
		IStatus status =
			new Status(IStatus.ERROR, "SubsystemWizard", IStatus.OK, message, null);
		throw new CoreException(status);
	}

	/**
	 * We will accept the selection in the workbench to see if
	 * we can initialize from it.
	 * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.selection = selection;
	}
	
	private String generateReplacement(String value){
		return "\\$\\{" + value + "\\}";
	}
}