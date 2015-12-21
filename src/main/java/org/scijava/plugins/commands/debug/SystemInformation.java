/*
 * #%L
 * Core commands for SciJava applications.
 * %%
 * Copyright (C) 2010 - 2015 Board of Regents of the University of
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

package org.scijava.plugins.commands.debug;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.app.App;
import org.scijava.app.AppService;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.PluginInfo;
import org.scijava.plugin.SciJavaPlugin;
import org.scijava.util.ClassUtils;
import org.scijava.util.Manifest;
import org.scijava.util.POM;

/**
 * Dumps the full system configuration, including installed libraries and Java
 * system properties.
 * 
 * @author Curtis Rueden
 */
@Plugin(type = Command.class, menuPath = "Plugins>Debug>System Information",
	headless = true)
public class SystemInformation implements Command {

	// -- Constants --

	private static final String NL = System.getProperty("line.separator");

	// -- Parameters --

	@Parameter
	private Context context;

	@Parameter
	private AppService appService;

	@Parameter
	private StatusService statusService;

	@Parameter
	private LogService log;

	@Parameter(label = "System Information", type = ItemIO.OUTPUT)
	private String info;

	// -- Runnable methods --

	@Override
	public void run() {
		statusService.showStatus("Gathering system information");

		final List<POM> poms = POM.getAllPOMs();

		int progress = 0, max = 10 + poms.size();
		statusService.showProgress(++progress, max);

		final StringBuilder sb = new StringBuilder();

		// dump basic version information (similar to the status bar)

		sb.append(appService.getApp().getInfo(false) + NL);

		statusService.showProgress(++progress, max);

		// dump information about available SciJava applications

		final Map<String, App> apps = appService.getApps();
		for (final String name : apps.keySet()) {
			final App app = apps.get(name);
			final Manifest manifest = app.getManifest();
			sb.append(NL);
			sb.append("-- Application: " + name + " --" + NL);
			sb.append("Title = " + app.getTitle() + NL);
			sb.append("Version = " + app.getVersion() + NL);
			sb.append("groupId = " + app.getGroupId() + NL);
			sb.append("artifactId = " + app.getArtifactId() + NL);
			if (manifest != null) {
				sb.append(getManifestData(manifest));
			}
		}

		statusService.showProgress(++progress, max);

		// dump all available Maven metadata on the class path

		// check for library version clashes
		final HashMap<String, POM> pomsByGA = new HashMap<String, POM>();
		for (final POM pom : poms) {
			final String ga = pom.getGroupId() + ":" + pom.getArtifactId();
			final POM priorPOM = pomsByGA.get(ga);
			if (priorPOM == null) {
				pomsByGA.put(ga, pom);
			}
			else {
				sb.append("[WARNING] Version clash for " + ga + ": " +
					pom.getVersion() + " shadows " + priorPOM.getVersion() + NL);
			}
		}

		statusService.showProgress(++progress, max);

		// output libraries in sorted order
		final ArrayList<POM> sortedPOMs = new ArrayList<POM>(poms);
		Collections.sort(sortedPOMs);
		for (final POM pom : sortedPOMs) {
			statusService.showProgress(++progress, max);

			final String pomPath = pom.getPath();
			final String groupId = pom.getGroupId();
			final String artifactId = pom.getArtifactId();
			final String version = pom.getVersion();
			final String name = pom.getProjectName();
			final String url = pom.getProjectURL();
			final String year = pom.getProjectInceptionYear();
			final String orgName = pom.getOrganizationName();
			final String orgURL = pom.getOrganizationURL();
			final String title = name == null ? groupId + ":" + artifactId : name;

			final String scmConnection = pom.cdata("//project/scm/connection");
			final String scmTag = pom.cdata("//project/scm/tag");
			String sourceRef = null;
			if (scmTag == null || scmTag.isEmpty() || scmTag.equals("HEAD") ||
				scmTag.equals("master"))
			{
				// look in the JAR manifest for the commit hash
				// TODO: Make POM API support obtaining the associated Manifest.

				// grab Implementation-Build entry out of the JAR manifest
				if (pomPath != null && pomPath.contains(".jar!")) {
					final String jarPath = pomPath.substring(0, pomPath.indexOf("!"));
					try {
						final URL jarURL = new URL("jar:" + jarPath + "!/");
						final JarURLConnection conn =
								(JarURLConnection) jarURL.openConnection();
						final String key = "Implementation-Build";
						sourceRef = conn.getManifest().getMainAttributes().getValue(key);
					}
					catch (final IOException e) {
						log.debug(e);
					}
				}
			}
			else {
				// ref is a valid tag
				sourceRef = scmTag;
			}

			sb.append(NL);
			sb.append("-- Library: " + title + " --" + NL);
			if (pomPath != null) sb.append("path = " + pomPath + NL);
			if (groupId != null) sb.append("groupId = " + groupId + NL);
			if (artifactId != null) sb.append("artifactId = " + artifactId + NL);
			if (version != null) sb.append("version = " + version + NL);
			if (url != null) sb.append("project URL = " + url + NL);
			if (year != null) sb.append("inception year = " + year + NL);
			if (orgName != null) sb.append("organization name = " + orgName + NL);
			if (orgURL != null) sb.append("organization URL = " + orgURL + NL);
			if (scmConnection != null) sb.append("scm = " + scmConnection + NL);
			if (sourceRef != null) sb.append("source ref = " + sourceRef + NL);
		}

		statusService.showProgress(++progress, max);

		// compute the set of known plugin types

		final List<PluginInfo<?>> plugins = context.getPluginIndex().getAll();
		final HashSet<Class<? extends SciJavaPlugin>> pluginTypeSet =
			new HashSet<Class<? extends SciJavaPlugin>>();
		for (final PluginInfo<?> plugin : plugins) {
			pluginTypeSet.add(plugin.getPluginType());
		}

		statusService.showProgress(++progress, max);

		// convert to a list of plugin types, sorted by fully qualified class name

		final ArrayList<Class<? extends SciJavaPlugin>> pluginTypes =
			new ArrayList<Class<? extends SciJavaPlugin>>(pluginTypeSet);
		Collections.sort(pluginTypes, new Comparator<Class<?>>() {

			@Override
			public int compare(final Class<?> c1, final Class<?> c2) {
				return ClassUtils.compare(c1, c2);
			}

		});

		statusService.showProgress(++progress, max);

		// dump the list of available plugins, organized by plugin type

		for (final Class<? extends SciJavaPlugin> pluginType : pluginTypes) {
			dumpPlugins(sb, pluginType);
		}

		statusService.showProgress(++progress, max);

		// dump system properties

		sb.append(NL);
		sb.append("-- System properties --" + NL);
		sb.append(getSystemProperties());

		statusService.showProgress(++progress, max);

		// dump environment variables

		sb.append(NL);
		sb.append("-- Environment variables --" + NL);
		sb.append(getEnvironmentVariables());

		statusService.showProgress(++progress, max);

		// dump miscellaneous extra information

		sb.append(NL);
		sb.append("-- Additional miscellany --" + NL);
		sb.append(getMiscellany());

		statusService.showProgress(++progress, max);

		info = sb.toString();

		statusService.clearStatus();
	}

	// -- Utility methods --

	public static String getSystemProperties() {
		return mapToString(System.getProperties());
	}

	public static String getEnvironmentVariables() {
		return mapToString(System.getenv());
	}

	public static String getMiscellany() {
		final HashMap<String, Object> miscellany = new HashMap<String, Object>();

		final JavaCompiler sjc = ToolProvider.getSystemJavaCompiler();
		final String sjcName = sjc == null ? null : sjc.getClass().getName();
		miscellany.put("System Java compiler", sjcName);

		final ClassLoader stcl = ToolProvider.getSystemToolClassLoader();
		final String stclName = stcl == null ? null : stcl.getClass().getName();
		miscellany.put("System tool class loader", stclName);

		return mapToString(miscellany);
	}

	public static String getManifestData(final Manifest manifest) {
		if (manifest == null) return null;
		return mapToString(manifest.getAll());
	}

	public static String mapToString(final Map<?, ?> map) {
		final StringBuilder sb = new StringBuilder();

		// sort keys by string representation
		final ArrayList<Object> keys = new ArrayList<Object>(map.keySet());
		Collections.sort(keys, new Comparator<Object>() {

			@Override
			public int compare(final Object o1, final Object o2) {
				if (o1 == null && o2 == null) return 0;
				if (o1 == null) return -1;
				if (o2 == null) return 1;
				return o1.toString().compareTo(o2.toString());
			}

		});

		for (final Object key : keys) {
			if (key == null) continue;
			final Object value = map.get(key);
			final String sKey = key.toString();
			final String sValue = value == null ? "(null)" : value.toString();

			if (sKey.endsWith(".dirs") || sKey.endsWith(".path")) {
				// split path and display values as a list
				final String[] dirs = sValue.split(Pattern.quote(File.pathSeparator));
				sb.append(sKey + " = {" + NL);
				for (final String dir : dirs) {
					sb.append("\t" + dir + NL);
				}
				sb.append("}" + NL);
			}
			else {
				// display a single key/value pair
				sb.append(sKey + " = " + sValue + NL);
			}
		}
		return sb.toString();
	}

	// -- Helper methods --

	private <PT extends SciJavaPlugin> void dumpPlugins(final StringBuilder sb,
		final Class<PT> pluginType)
	{
		final List<PluginInfo<PT>> plugins =
			context.getPluginIndex().getPlugins(pluginType);

		// count the number of plugins whose type matches exactly (not sub-types)
		int pluginCount = 0;
		for (final PluginInfo<PT> plugin : plugins) {
			if (pluginType == plugin.getPluginType()) pluginCount++;
		}
		if (pluginCount == 0) return;

		sb.append(NL);
		sb.append("-- " + pluginCount + " " + pluginType.getName() +
			" plugins --" + NL);
		for (final PluginInfo<PT> plugin : plugins) {
			if (pluginType != plugin.getPluginType()) continue;
			sb.append(plugin + NL);
		}
	}

}
