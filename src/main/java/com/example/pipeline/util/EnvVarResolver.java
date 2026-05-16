package com.example.pipeline.util;

import com.example.pipeline.service.credential.CredentialManager;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolver for ${VAR} placeholders in strings and maps.
 * Resolution order:
 * 1. Environment variables
 * 2. System properties
 * 3. Stored encrypted credentials (if CredentialManager provided)
 */
public class EnvVarResolver {
    private static final Pattern pattern = Pattern.compile("\\$\\{([A-Za-z0-9_]+)\\}");
    private static CredentialManager credentialManager;

    /**
     * Set the credential manager for secure credential resolution.
     * Call this during application startup to enable credential resolution.
     */
    public static void setCredentialManager(CredentialManager manager) {
        credentialManager = manager;
    }

    /**
     * Resolve placeholders in a single string.
     * Tries environment variables, system properties, then stored credentials.
     */
    public static String resolve(String input) {
        if (input == null) return null;
        Matcher m = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String val = System.getenv(name);
            if (val == null) val = System.getProperty(name);
            if (val == null && credentialManager != null) {
                val = credentialManager.getCredential(name).orElse(null);
            }
            if (val == null) val = "";
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Recursively resolve placeholders inside a Map.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolveMap(Map<String, Object> input) {
        if (input == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) {
                out.put(e.getKey(), resolve((String) v));
            } else if (v instanceof Map) {
                out.put(e.getKey(), resolveMap((Map<String, Object>) v));
            } else if (v instanceof List) {
                out.put(e.getKey(), resolveList((List<Object>) v));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> resolveList(List<Object> input) {
        List<Object> out = new ArrayList<>();
        for (Object v : input) {
            if (v instanceof String) out.add(resolve((String) v));
            else if (v instanceof Map) out.add(resolveMap((Map<String, Object>) v));
            else if (v instanceof List) out.add(resolveList((List<Object>) v));
            else out.add(v);
        }
        return out;
    }
}
