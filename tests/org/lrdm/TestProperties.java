package org.lrdm;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

// In TestProperties.java
public class TestProperties {
    static final Properties props = new Properties();

    public static void loadProperties(String config) throws IOException {
        props.load(new FileReader(config));
    }

    // ✅ Neue public Getter-Methode hinzufügen
    public static Properties getProps() {
        return props;
    }
}