package com.taskmanagement.api.infra.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilitário central de leitura de variáveis de ambiente.
 * Ordem de precedência: System.getenv() > arquivo .env local > valor padrão.
 */
public final class Config {

    private static final Logger LOGGER = Logger.getLogger(Config.class.getName());
    private static final Map<String, String> DOTENV_VARS = loadDotEnv();

    private Config() {
    }

    public static String get(String key, String defaultValue) {
        String sysVal = System.getenv(key);
        if (sysVal != null && !sysVal.isBlank()) {
            return sysVal.trim();
        }
        String dotVal = DOTENV_VARS.get(key);
        if (dotVal != null && !dotVal.isBlank()) {
            return dotVal.trim();
        }
        return defaultValue;
    }

    public static String require(String key) {
        String val = get(key, null);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória não definida: " + key);
        }
        return val;
    }

    public static int getInt(String key, int defaultValue) {
        String val = get(key, null);
        return (val != null) ? Integer.parseInt(val) : defaultValue;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key, null);
        return (val != null) ? Boolean.parseBoolean(val) : defaultValue;
    }

    private static Map<String, String> loadDotEnv() {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath) || !Files.isRegularFile(envPath)) {
            return Collections.emptyMap();
        }

        Map<String, String> vars = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(envPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    vars.put(key, val);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Não foi possível ler o arquivo .env: {0}", e.getMessage());
        }
        return Collections.unmodifiableMap(vars);
    }
}
