package org.ergoplatform.appkit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Paths;

/**
 * Configuration parameters of ErgoTool utility
 */
public class ErgoToolConfig {
    private ErgoNodeConfig node;
    private ToolParameters parameters;

    /**
     * Returns Ergo node configuration
     */
    public ErgoNodeConfig getNode() {
        return node;
    }

    /**
     * Config section with Named parameters
     *
     * @return {@code HashMap<String,String>} of Name-Value pairs
     */
    public ToolParameters getParameters() {
        return parameters;
    }

    /**
     * Load config from the given reader.
     *
     * @param reader reader of the config json.
     * @return ErgoToolConfig created form the file content.
     */
    public static ErgoToolConfig load(Reader reader) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(reader, ErgoToolConfig.class);
    }

    /**
     * Load config from the given file.
     *
     * @param file file with config json.
     * @return ErgoToolConfig created form the file content.
     */
    public static ErgoToolConfig load(File file) throws FileNotFoundException {
        Gson gson = new GsonBuilder().create();
        FileReader reader = new FileReader(file);
        return gson.fromJson(reader, ErgoToolConfig.class);
    }

    /**
     * Load config from the given file.
     *
     * @param fileName name of the file relative to the current directory.
     *                 The file is resolved using {@link File#getAbsolutePath()}.
     * @return ErgoToolConfig created form the file content.
     */
    public static ErgoToolConfig load(String fileName) throws FileNotFoundException {
        File file = Paths.get(fileName).toAbsolutePath().toFile();
        return load(file);
    }

}
