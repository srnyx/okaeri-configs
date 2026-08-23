package eu.okaeri.configs.yaml.bukkit;

import eu.okaeri.configs.configurer.Configurer;
import eu.okaeri.configs.format.yaml.YamlSourceWalker;
import eu.okaeri.configs.postprocessor.ConfigPostprocessor;
import eu.okaeri.configs.schema.ConfigDeclaration;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.BaseRepresenter;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer.YamlConfigurationReflection.*;

@Accessors(chain = true)
public class YamlBukkitConfigurer extends Configurer {

    @Setter private String commentPrefix = "# ";
    @Setter private int indicatorIndent = 0;
    @Setter private boolean useDoubleQuotesForMultilineStrings = false;

    @Override
    public List<String> getExtensions() {
        return Arrays.asList("yml", "yaml");
    }

    @Override
    public boolean isCommentLine(String line) {
        return line.trim().startsWith("#");
    }

    @Override
    public Map<String, Object> load(@NonNull InputStream inputStream, @NonNull ConfigDeclaration declaration) throws Exception {

        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator((char) 29);
        config.loadFromString(ConfigPostprocessor.of(inputStream).getContext());

        return this.memorySectionToMap(config);
    }

    @Override
    public void write(@NonNull OutputStream outputStream, @NonNull Map<String, Object> data, @NonNull ConfigDeclaration declaration) throws Exception {

        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator((char) 29); // 'group separator': disables dot parsing in set/get
        if (this.indicatorIndent > 0) {
            try {
                DumperOptions dumperOptions = (DumperOptions) DUMPER_OPTIONS_FIELD.get(config);
                if (DUMPER_INDENT_WITH_INDICATOR != null) DUMPER_INDENT_WITH_INDICATOR.invoke(dumperOptions, true);
                dumperOptions.setIndicatorIndent(this.indicatorIndent);
            } catch (Exception ignored) {}
        }
        if (this.useDoubleQuotesForMultilineStrings) this.patchMultilineStringStyle(config);

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }

        ConfigPostprocessor.of(config.saveToString())
            .removeLines(line -> line.startsWith(this.commentPrefix.trim()))
            .removeLinesUntil(line -> line.chars().anyMatch(x -> !Character.isWhitespace(x)))
            .updateContext(ctx -> YamlSourceWalker.of(ctx).insertComments(declaration, this.commentPrefix))
            .write(outputStream);
    }

    /**
     * Patch the YamlConfiguration to use double-quoted style for multiline strings instead of block style.
     * <br>Otherwise, an ugly, difficult-to-read styling is used for multiline strings.
     */
    private void patchMultilineStringStyle(@NonNull YamlConfiguration config) {
        if (REPRESENTER_FIELD == null || REPRESENTERS_FIELD == null || REPRESENT_SCALAR_METHOD == null) return;
        try {
            Object representer = REPRESENTER_FIELD.get(config);
            Map<Class<?>, Represent> representers = (Map<Class<?>, Represent>) REPRESENTERS_FIELD.get(representer);
            Represent original = representers.get(String.class);
            if (original == null) return;

            representers.put(String.class, data -> {
                String value = (String) data;
                if (value.indexOf('\n') >= 0) {
                    // Has newlines: use double-quotes
                    try {
                        return (Node) REPRESENT_SCALAR_METHOD.invoke(representer, Tag.STR, value, DOUBLE_QUOTED_STYLE);
                    } catch (Exception e) {
                        return original.representData(data);
                    }
                }
                // No newlines: default styling
                return original.representData(data);
            });
        } catch (Exception ignored) {}
    }

    private Map<String, Object> memorySectionToMap(MemorySection section) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof MemorySection) {
                value = this.memorySectionToMap((MemorySection) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static class YamlConfigurationReflection {

        // --- DumperOptions --- //

        public static final Field DUMPER_OPTIONS_FIELD;
        public static final Method DUMPER_INDENT_WITH_INDICATOR;

        static {
            // DUMPER_OPTIONS_FIELD
            Field dumperOptionsField = tryDumperField("yamlDumperOptions");
            if (dumperOptionsField == null) dumperOptionsField = tryDumperField("yamlOptions");
            if (dumperOptionsField == null) {
                for (Field f : YamlConfiguration.class.getDeclaredFields()) {
                    if (isDumperOptions(f)) {
                        dumperOptionsField = f;
                        break;
                    }
                }
            }
            if (dumperOptionsField != null) dumperOptionsField.setAccessible(true);
            DUMPER_OPTIONS_FIELD = dumperOptionsField;

            // DUMPER_INDENT_WITH_INDICATOR
            Method method = null;
            try {
                method = DumperOptions.class.getDeclaredMethod("setIndentWithIndicator", boolean.class);
            } catch (Exception ignored) {}
            DUMPER_INDENT_WITH_INDICATOR = method;
        }

        private static boolean isDumperOptions(@NonNull Field field) {
            return DumperOptions.class.isAssignableFrom(field.getType());
        }

        private static Field tryDumperField(@NonNull String name) {
            try {
                Field field = YamlConfiguration.class.getDeclaredField(name);
                if (isDumperOptions(field)) return field;
            } catch (Exception ignored) {}
            return null;
        }


        // --- Representer --- //

        public static final Field REPRESENTER_FIELD;
        public static final Field REPRESENTERS_FIELD;
        public static final Method REPRESENT_SCALAR_METHOD;
        public static final Object DOUBLE_QUOTED_STYLE;

        static {
            // REPRESENTER_FIELD
            Field representerField = null;
            try {
                Field field1 = YamlConfiguration.class.getDeclaredField("representer");
                if (isRepresenter(field1)) representerField = field1;
            } catch (Exception ignored1) {}
            if (representerField == null) {
                for (Field f : YamlConfiguration.class.getDeclaredFields()) {
                    if (isRepresenter(f)) {
                        representerField = f;
                        break;
                    }
                }
            }
            if (representerField != null) representerField.setAccessible(true);
            REPRESENTER_FIELD = representerField;

            // REPRESENTERS_FIELD
            Field representersField = null;
            try {
                representersField = BaseRepresenter.class.getDeclaredField("representers");
                representersField.setAccessible(true);
            } catch (Exception ignored) {}
            REPRESENTERS_FIELD = representersField;

            // REPRESENT_SCALAR_METHOD
            Method representScalarMethod = null;
            Object doubleQuotedStyle = null;
            try {
                representScalarMethod = BaseRepresenter.class.getDeclaredMethod("representScalar", Tag.class, String.class, DumperOptions.ScalarStyle.class);
                doubleQuotedStyle = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
            } catch (Exception e) {
                try {
                    representScalarMethod = BaseRepresenter.class.getDeclaredMethod("representScalar", Tag.class, String.class, Character.class);
                    doubleQuotedStyle = DumperOptions.ScalarStyle.DOUBLE_QUOTED.getChar();
                } catch (Exception ignored) {}
            }
            if (representScalarMethod != null) representScalarMethod.setAccessible(true);
            REPRESENT_SCALAR_METHOD = representScalarMethod;
            DOUBLE_QUOTED_STYLE = doubleQuotedStyle;
        }

        private static boolean isRepresenter(@NonNull Field field) {
            return Representer.class.isAssignableFrom(field.getType());
        }
    }
}
