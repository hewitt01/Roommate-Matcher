package com.roommatch.config;

/** Configuration is missing or unusable. The message never contains secret values. */
public class ConfigException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }
}
