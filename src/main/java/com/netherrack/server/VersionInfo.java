package com.netherrack.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * What the server is and what it's running on: its name and version, the git commit it was
 * built from, and the Java and operating system it's running under. This is the one place
 * those are kept, so the startup log and /version can't disagree.
 * <p>
 * The version comes from pom.xml (copied into version.properties when building), and the
 * commit from git.properties, which the build writes when it's run inside a git checkout.
 * A build without them - from a downloaded source archive, or run from an IDE - still works:
 * what's missing shows as "unknown".
 */
public final class VersionInfo {

    public static final String NAME = "Netherrack";

    private static final String UNKNOWN = "unknown";

    /** The version in pom.xml, e.g. "0.1.0-alpha". */
    public static final String VERSION = load("/version.properties").getProperty("version", UNKNOWN);

    /** The short id of the git commit this was built from. */
    public static final String GIT_COMMIT = load("/git.properties").getProperty("git.commit.id.abbrev", UNKNOWN);

    /** The Java it's running on, e.g. "17.0.9 (Eclipse Adoptium)". */
    public static final String JAVA_VERSION = property("java.version") + " (" + property("java.vendor") + ")";

    /** The operating system, e.g. "Linux 6.6.1 (amd64)". */
    public static final String OPERATING_SYSTEM =
            property("os.name") + " " + property("os.version") + " (" + property("os.arch") + ")";

    private VersionInfo() {
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? UNKNOWN : value;
    }

    private static Properties load(String resource) {
        Properties properties = new Properties();
        try (InputStream in = VersionInfo.class.getResourceAsStream(resource)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Left empty: the values fall back to "unknown".
        }
        return properties;
    }
}
