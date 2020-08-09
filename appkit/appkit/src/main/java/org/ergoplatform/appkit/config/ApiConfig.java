package org.ergoplatform.appkit.config;

/**
 * Ergo node API connection parameters
 */
public class ApiConfig {
    private String apiUrl;
    private String apiKey;

    /**
     * Url of the Ergo node API end point
     */
    public String getApiUrl() {
        return apiUrl;
    }

    /**
     * ApiKey which is used for Ergo node API authentication.
     * This is a secrete key whose hash was used in Ergo node config.
     */
    public String getApiKey() {
        return apiKey;
    }
}

