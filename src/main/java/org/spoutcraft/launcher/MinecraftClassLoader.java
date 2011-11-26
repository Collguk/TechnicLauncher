/*
 * This file is part of Spoutcraft Launcher (http://wiki.getspout.org/).
 * 
 * Spoutcraft Launcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Spoutcraft Launcher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.spoutcraft.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MinecraftClassLoader extends URLClassLoader{
	private HashMap<String, Class<?>> loadedClasses = new HashMap<String, Class<?>>(1000);
	private File spoutcraft = null;
	
	public MinecraftClassLoader(URL[] urls, ClassLoader parent, File spoutcraft) {
		super(urls, parent);
		this.spoutcraft = spoutcraft;
	}
	
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		byte classByte[];
		Class<?> result = null;

		result = loadedClasses.get(name); //checks in cached classes  
		if (result != null) {
			return result;
		}

		try {
			return findSystemClass(name);  
		} catch (Exception ignore) { }

		try {
			JarFile jar = new JarFile(spoutcraft);  
			JarEntry entry = jar.getJarEntry(name + ".class");  
			InputStream is = jar.getInputStream(entry);  
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			int next = is.read();
			while (-1 != next) {
				byteStream.write(next);
				next = is.read();
			}

			classByte = byteStream.toByteArray();
			result = defineClass(name, classByte, 0, classByte.length, (CodeSource)null);
			loadedClasses.put(name, result);
			return result;
		} catch (Exception e) {
			return super.findClass(name);
		}
	}
}
