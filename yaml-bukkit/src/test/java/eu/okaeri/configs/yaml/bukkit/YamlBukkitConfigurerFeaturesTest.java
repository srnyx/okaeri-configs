package eu.okaeri.configs.yaml.bukkit;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests YamlBukkitConfigurer-specific features.
 * Only tests for backend-specific functionality not covered by parameterized tests.
 */
class YamlBukkitConfigurerFeaturesTest {

    @Test
    void testCustomCommentPrefix() throws Exception {
        // Given: Configurer with custom comment prefix
        YamlBukkitConfigurer configurer = new YamlBukkitConfigurer();
        configurer.setCommentPrefix("#> ");

        CommentedConfig config = ConfigManager.create(CommentedConfig.class);
        config.withConfigurer(configurer);

        // When: Write to OutputStream
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        config.save(output);
        String yaml = output.toString();

        // Then: Custom comment prefix is used
        assertThat(yaml).contains("#> This is a simple field comment");
        assertThat(yaml).doesNotContain("# This is a simple field comment");
    }

    @Test
    void testMultilineStringUsesQuotedEscapeNotBlockScalar() throws Exception {
        // Given: Config with a string value containing embedded newlines
        YamlBukkitConfigurer configurer = new YamlBukkitConfigurer();

        MultilineConfig config = ConfigManager.create(MultilineConfig.class);
        config.withConfigurer(configurer);

        // When: Write to OutputStream
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        config.save(output);
        String yaml = output.toString();

        // Then: Quoted escape style is used instead of a literal block scalar
        assertThat(yaml).contains("start: \"\\nhello\\nworld\\n\"");
        assertThat(yaml).doesNotContain("start: |");

        // And: Unaffected plain string values keep their existing style
        assertThat(yaml).contains("plainField: plain");

        // And: Round-trips back to the exact original value
        MultilineConfig loaded = ConfigManager.create(MultilineConfig.class);
        loaded.withConfigurer(new YamlBukkitConfigurer());
        loaded.load(new java.io.ByteArrayInputStream(output.toByteArray()));
        assertThat(loaded.getStart()).isEqualTo(config.getStart());
    }

    // Test config classes

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class CommentedConfig extends OkaeriConfig {
        @Comment("This is a simple field comment")
        private String simpleField = "default";

        @Comment({"Multi-line comment", "Line 2 of comment"})
        private int numberField = 42;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class MultilineConfig extends OkaeriConfig {
        private String start = "\nhello\nworld\n";
        private String plainField = "plain";
    }
}
