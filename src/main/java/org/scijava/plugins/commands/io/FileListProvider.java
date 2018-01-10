package org.scijava.plugins.commands.io;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

/**
 * Returns a list of files with the given extensions
 * 
 * @author Jan Eglinger
 */
@Plugin(type = Command.class, headless = true)
public class FileListProvider extends ContextCommand {

	@Parameter
	private LogService logService;

	@Parameter(style = FileWidget.DIRECTORY_STYLE)
	private File inputFolder;

	@Parameter(required = false)
	private String extensions = "";

	@Parameter(required = false)
	private boolean recursive = true;

	@Parameter(type = ItemIO.OUTPUT)
	private File[] files;

	@Override
	public void run() {
		String[] extensionList = extensions.trim().split("/");

		// Create FileFilter from extensions
		FileFilter filter = new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (extensionList.length == 0)
					return pathname.isFile();

				for (String ext : extensionList) {
					if (pathname.getName().toLowerCase()
							.endsWith(ext.toLowerCase())
							&& pathname.isFile())
						return true;
				}
				return false;
			}
		};

		if (recursive) {
			// Recursively list files
			try {
				files = (Files.walk(inputFolder.toPath()).filter(path -> filter
						.accept(path.toFile()))).map(path -> path.toFile())
						.toArray(File[]::new);
			} catch (IOException exc) {
				logService
						.error("Error when trying to retrieve file list", exc);
			}
		} else {
			// Get non-recursive only for this folder
			files = inputFolder.listFiles(filter);
		}
	}
}
