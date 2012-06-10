/*
 * This file is part of Spoutcraft Launcher.
 *
 * Copyright (c) 2011-2012, SpoutDev <http://www.spout.org/>
 * Spoutcraft Launcher is licensed under the SpoutDev License Version 1.
 *
 * Spoutcraft Launcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the SpoutDev License Version 1.
 *
 * Spoutcraft Launcher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the SpoutDev License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://www.spout.org/SpoutDevLicenseV1.txt> for the full license,
 * including the MIT license.
 */
package org.spoutcraft.launcher.launch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.ClosedByInterruptException;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.apache.commons.io.FileUtils;
import org.spoutcraft.launcher.api.SpoutcraftDirectories;

public class MinecraftClassLoader extends URLClassLoader {
	private HashMap<String, Class<?>> loadedClasses = new HashMap<String, Class<?>>(10000);
	private HashSet<String> preloaded = new HashSet<String>();
	private HashMap<String, File> classLocations = new HashMap<String, File>(10000);

	public MinecraftClassLoader(ClassLoader parent, File spoutcraft, File[] libraries) {
		super(new URL[0], parent);

		//Move all of the jars we want to use to a temp folder (so we don't create file hooks on them)
		File tempDir = getTempDirectory();
		for (File f : libraries) {
			try {
				File replacement = new File(tempDir, f.getName());
				FileUtils.copyFile(f, replacement);
				this.addURL(replacement.toURI().toURL());
				index(replacement);
			} catch (ClosedByInterruptException e) {
				//Ignore, assume we interrupted for a reason
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			File tempSpoutcraft = new File(tempDir, spoutcraft.getName());
			FileUtils.copyFile(spoutcraft, tempSpoutcraft);
			spoutcraft = tempSpoutcraft;
			this.addURL(spoutcraft.toURI().toURL());
			index(spoutcraft);
		} catch (ClosedByInterruptException e) {
			//Ignore, assume we interrupted for a reason
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private File getTempDirectory() {
		SpoutcraftDirectories dir = new SpoutcraftDirectories();
		int index = 0;
		while(true) {
			File tempDir = new File(dir.getBinDir(), "temp_" + index);
			if (!tempDir.isDirectory() && !tempDir.exists()) {
				tempDir.mkdirs();
				return tempDir;
			}
			index++;
		}
	}
	
	private void index(File file) throws IOException{
		JarFile jar = new JarFile(file);
		Enumeration<JarEntry> i = jar.entries();
		while (i.hasMoreElements()) {
			JarEntry entry = i.nextElement();
			if (entry.getName().endsWith(".class")) {
				String name = entry.getName();
				name = name.replace("/", ".").substring(0, name.length() - 6);
				classLocations.put(name, file);
			}
		}
	}

	public int preloadClasses(int amount) {
		Iterator<Entry<String, File>> i = classLocations.entrySet().iterator();
		int preloaded = 0;
		while (i.hasNext() && preloaded < amount) {

			if (Thread.currentThread().isInterrupted()) {
				break;
			}

			Entry<String, File> entry = i.next();
			String className = entry.getKey();
			if (!this.preloaded.contains(className)) {
				try {
					this.loadClass(className);
					preloaded++;
				} catch (Throwable ignore) { }
			}
			this.preloaded.add(className);
		}
		return preloaded;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Class<?> result = null;
		result = loadedClasses.get(name); // Checks in cached classes
		if (result != null) {
			return result;
		}

		File f = classLocations.get(name);
		if (f != null) {
			result = findClassInjar(name, f);
			if (result != null) {
				return result;
			}
		}

		return super.findClass(name);
	}

	private Class<?> findClassInjar(String name, File file) throws ClassNotFoundException {
		byte classByte[];
		Class<?> result = null;
		try {
			JarFile jar = new JarFile(file);
			JarEntry entry = jar.getJarEntry(name.replace(".", "/") + ".class");
			if (entry != null) {
				InputStream is = jar.getInputStream(entry);
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
				int next = is.read();
				while (-1 != next) {
					byteStream.write(next);
					next = is.read();
				}

				classByte = byteStream.toByteArray();
				result = defineClass(name, classByte, 0, classByte.length, new CodeSource(file.toURI().toURL(), (CodeSigner[]) null));
				loadedClasses.put(name, result);
				return result;
			}
		} catch (ZipException zipEx) {
			System.out.println("Failed to open " + name + " from " + file.getPath());
			zipEx.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
