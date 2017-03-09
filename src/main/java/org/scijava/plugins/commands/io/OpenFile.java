/*
 * #%L
 * Core commands for SciJava applications.
 * %%
 * Copyright (C) 2010 - 2017 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.plugins.commands.io;

import java.io.File;
import java.io.IOException;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.ContextCommand;
import org.scijava.io.IOPlugin;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.plugin.Attr;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

/**
 * Opens the selected file.
 * 
 * @author Curtis Rueden
 * @author Mark Hiner
 */
@Plugin(type = Command.class, iconPath = "/icons/commands/folder_picture.png",
	menu = {
		@Menu(label = MenuConstants.FILE_LABEL, weight = MenuConstants.FILE_WEIGHT,
			mnemonic = MenuConstants.FILE_MNEMONIC),
		@Menu(label = "Open...", weight = 1, mnemonic = 'o', accelerator = "^O") },
	attrs = { @Attr(name = "no-legacy") })
public class OpenFile extends ContextCommand {

	@Parameter
	private LogService log;

	@Parameter
	private IOService ioService;

	@Parameter
	private UIService uiService;

	@Parameter(label = "File to open")
	private File inputFile;

	@Parameter(type = ItemIO.OUTPUT, label = "Data")
	private Object data;

	@Override
	public void run() {
		try {
			final String source = inputFile.getAbsolutePath();
			final IOPlugin<?> opener = ioService.getOpener(source);
			if (opener == null) {
				error("No appropriate format found: " + source);
				return;
			}
			data = opener.open(source);
			if (data == null) {
				cancel(null);
				return;
			}
		}
		catch (final IOException exc) {
			log.error(exc);
			error(exc.getMessage());
		}
	}

	public File getInputFile() {
		return inputFile;
	}

	public void setInputFile(final File inputFile) {
		this.inputFile = inputFile;
	}

	public Object getData() {
		return data;
	}

	public void setData(final Object data) {
		this.data = data;
	}

	// -- Helper methods --

	private void error(final String message) {
		uiService.showDialog(message, DialogPrompt.MessageType.ERROR_MESSAGE);
	}

}
