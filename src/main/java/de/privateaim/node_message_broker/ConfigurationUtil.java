package de.privateaim.node_message_broker;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Bundles utilities that are supposed to be used for configuration purposes (i.e. in classes annotated with
 * configuration).
 */
public final class ConfigurationUtil {

    private ConfigurationUtil() {
    }

    /**
     * Reads the contents of an external file.
     *
     * @param filePath path to the file that shall be read
     * @return The content of the file.
     * @throws IOException If the file cannot be read.
     */
    public static byte[] readExternalFileContent(String filePath) throws IOException {
        try (var fis = new FileInputStream(filePath)) {
            return fis.readAllBytes();
        }
    }
}
