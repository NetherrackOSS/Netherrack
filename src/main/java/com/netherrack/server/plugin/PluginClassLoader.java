package com.netherrack.server.plugin;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Loads one plugin's classes from its jar while sharing the API with the server.
 * <p>
 * Java and {@code com.netherrack.api.*} classes are always parent-first, so a plugin cannot
 * bring a second copy of the contracts the server itself compiled against. Everything else is
 * child-first, which lets a plugin bundle its own libraries without those libraries colliding
 * with versions the server happens to use.
 */
public final class PluginClassLoader extends URLClassLoader {

    private static final String[] PARENT_FIRST = {"java.", "javax.", "jdk.", "com.netherrack.api."};

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = parentFirst(name) ? getParent().loadClass(name) : findPluginClassOrParent(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    private Class<?> findPluginClassOrParent(String name) throws ClassNotFoundException {
        try {
            return findClass(name);
        } catch (ClassNotFoundException e) {
            return getParent().loadClass(name);
        }
    }

    private static boolean parentFirst(String name) {
        for (String prefix : PARENT_FIRST) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
