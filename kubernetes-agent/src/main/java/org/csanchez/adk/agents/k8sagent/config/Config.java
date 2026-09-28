package org.csanchez.adk.agents.k8sagent.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    
    static {
        try {
            // Load .env if it exists, and copy values to System properties
            Dotenv.configure()
                  .ignoreIfMissing()
                  .systemProperties()
                  .load();
        } catch (Exception e) {
            logger.warn("Failed to load .env file: {}", e.getMessage());
        }
    }

    /**
     * Gets a configuration value, checking environment variables first, then system properties.
     * @param key the configuration key
     * @param defaultValue the default value to return if not found
     * @return the configuration value
     */
    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            value = System.getProperty(key);
        }
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Gets a configuration value, checking environment variables first, then system properties.
     * @param key the configuration key
     * @return the configuration value, or null if not found
     */
    public static String get(String key) {
        return get(key, null);
    }
}
