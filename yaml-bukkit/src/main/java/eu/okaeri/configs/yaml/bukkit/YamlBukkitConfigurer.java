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
            } catch (final Exception ignored) {}
        }

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }

        ConfigPostprocessor.of(config.saveToString())
            .removeLines(line -> line.startsWith(this.commentPrefix.trim()))
            .removeLinesUntil(line -> line.chars().anyMatch(x -> !Character.isWhitespace(x)))
            .updateContext(ctx -> YamlSourceWalker.of(ctx).insertComments(declaration, this.commentPrefix))
            .write(outputStream);
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
        public static final Field DUMPER_OPTIONS_FIELD;
        public static final Method DUMPER_INDENT_WITH_INDICATOR;

        static {
            // DUMPER_OPTIONS_FIELD
            Field field = tryDumperField("yamlDumperOptions");
            if (field == null) field = tryDumperField("yamlOptions");
            if (field == null) {
                for (final Field f : YamlConfiguration.class.getDeclaredFields()) {
                    if (fieldIsDumperOptions(f)) {
                        field = f;
                        break;
                    }
                }
            }
            if (field != null) field.setAccessible(true);
            DUMPER_OPTIONS_FIELD = field;

            // DUMPER_INDENT_WITH_INDICATOR
            Method method = null;
            try {
                method = DumperOptions.class.getDeclaredMethod("setIndentWithIndicator", boolean.class);
            } catch (final Exception ignored) {}
            DUMPER_INDENT_WITH_INDICATOR = method;
        }

        private static boolean fieldIsDumperOptions(@NonNull Field field) {
            return field.getType() == DumperOptions.class;
        }

        private static Field tryDumperField(@NonNull String name) {
            try {
                Field field = YamlConfiguration.class.getDeclaredField(name);
                if (fieldIsDumperOptions(field)) return field;
            } catch (final Exception ignored) {}
            return null;
        }
    }
}
