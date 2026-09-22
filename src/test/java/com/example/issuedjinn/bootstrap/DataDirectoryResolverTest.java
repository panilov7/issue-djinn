package com.example.issuedjinn.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@link DataDirectoryResolver} to the data-directory expression in
 * {@code application.properties}. The resolver mirrors
 * {@code issue-djinn.data.dir=${ISSUE_DJINN_DATA_DIR:${user.home}/.issue-djinn}}
 * because it runs in {@code Application.main}, before Quarkus config exists —
 * and the mirror is the drift risk: this test reads the real properties line
 * and drives the resolver through the env-var name and default that line
 * declares, so editing one side without the other fails here.
 */
class DataDirectoryResolverTest {

    /** The shape of the properties expression: an env-var override with a fallback default. */
    private static final Pattern OVERRIDABLE_PROPERTY = Pattern.compile("^\\$\\{([A-Z0-9_]+):(.+)}$");

    private static final UnaryOperator<String> NO_ENV = name -> null;

    @Test
    void theEnvironmentVariableThePropertiesDeclareWinsWhenSet() throws IOException {
        OverrideExpression expression = dataDirExpression();
        Path override = Path.of("/tmp", "issue-djinn-override");

        Path resolved = DataDirectoryResolver.resolve(name -> expression.envVar.equals(name)
                ? override.toString()
                : null);

        assertEquals(override, resolved);
    }

    @Test
    void theDefaultThePropertiesDeclareAppliesWhenTheEnvironmentVariableIsAbsent() throws IOException {
        OverrideExpression expression = dataDirExpression();

        Path resolved = DataDirectoryResolver.resolve(NO_ENV);

        assertEquals(expression.evaluatedDefault(), resolved);
    }

    /**
     * The {@code issue-djinn.data.dir} line from {@code application.properties},
     * unpacked into its env-var name and its default expression.
     */
    private record OverrideExpression(String envVar, String defaultExpression) {
        /** The default expression with its system-property references evaluated, e.g. {@code ${user.home}}. */
        Path evaluatedDefault() {
            String evaluated = defaultExpression;
            Matcher reference = Pattern.compile("\\$\\{([^}]+)}").matcher(evaluated);
            StringBuilder result = new StringBuilder();
            while (reference.find()) {
                String value = System.getProperty(reference.group(1));
                if (value == null) {
                    throw new IllegalStateException(
                            "application.properties default references unknown system property "
                                    + reference.group(1));
                }
                reference.appendReplacement(result, Matcher.quoteReplacement(value));
            }
            reference.appendTail(result);
            return Path.of(result.toString());
        }
    }

    /** Reads the real properties file and unpacks {@code issue-djinn.data.dir} into the override shape. */
    private static OverrideExpression dataDirExpression() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = DataDirectoryResolverTest.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException("application.properties not on the classpath");
            }
            properties.load(in);
        }
        String value = properties.getProperty("issue-djinn.data.dir");
        if (value == null) {
            throw new IllegalStateException("issue-djinn.data.dir is not defined in application.properties");
        }
        Matcher expression = OVERRIDABLE_PROPERTY.matcher(value);
        if (!expression.matches()) {
            throw new IllegalStateException("issue-djinn.data.dir no longer has the env-var-override shape: "
                    + value);
        }
        return new OverrideExpression(expression.group(1), expression.group(2));
    }
}