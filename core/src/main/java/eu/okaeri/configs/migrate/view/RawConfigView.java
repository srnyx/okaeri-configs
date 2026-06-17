package eu.okaeri.configs.migrate.view;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.configurer.Configurer;
import eu.okaeri.configs.schema.GenericsDeclaration;
import eu.okaeri.configs.serdes.SerdesContext;
import eu.okaeri.configs.serdes.TypedKeyReader;
import eu.okaeri.configs.serdes.TypedKeyWriter;
import lombok.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provides raw key-value access to config data for migrations.
 * <p>
 * Supports dot-separated nested key paths (e.g., "section.subsection.key").
 * Implements both {@link TypedKeyReader} and {@link TypedKeyWriter} for
 * automatic type resolution and simplification.
 * <p>
 * All RAW reads/writes operate directly on {@code config.getInternalState()}
 * without triggering deserialization. A single {@code config.update()} call
 * at the end of ALL migrations syncs internalState to Java fields.
 */
public class RawConfigView implements TypedKeyReader, TypedKeyWriter {

    private final OkaeriConfig config;
    private final String nestedSeparator;

    public RawConfigView(@NonNull OkaeriConfig config, String nestedSeparator) {
        this.config = config;
        this.nestedSeparator = nestedSeparator;

        /*
         * Seeds internalState from field values if it hasn't been loaded yet.
         * This makes getRaw/exists work on freshly-created configs (no load() called),
         * while being a no-op when internalState was already populated by load().
         * <p>
         * Removing this will cause set(String, Object) to break for
         * configs that haven't used load() yet (not migrations).
         * <br>See RawConfigViewTest for examples.
         */
        if (!this.getInternalState().isEmpty()) return;
        this.getInternalState().putAll(this.config.asMap());
    }

    public RawConfigView(@NonNull OkaeriConfig config) {
        this(config, "\\.");
    }

    // ==================== INTERFACE REQUIREMENTS ====================

    @Override
    public Configurer getConfigurer() {
        return this.config.getConfigurer();
    }

    @Override
    public SerdesContext getReaderContext(@NonNull String key) {
        return SerdesContext.of(this.getConfigurer(), this.config.getContext(), null);
    }

    @Override
    public SerdesContext getWriterContext(@NonNull String key) {
        return SerdesContext.of(this.getConfigurer(), this.config.getContext(), null);
    }

    @Override
    public Object getRaw(@NonNull String key) {
        return this.valueExtract(this.config.getInternalState(), key);
    }

    @Override
    public Object set(@NonNull String key, Object value, GenericsDeclaration genericType) {
        Object old = TypedKeyWriter.super.set(key, value, genericType);
        this.config.update();
        return old;
    }

    @Override
    public Object setRaw(@NonNull String key, Object value) {
        return this.valuePut(this.config.getInternalState(), key, value);
    }

    @Override
    public Object getRawOrNull(@NonNull String key) {
        return this.getRaw(key);
    }

    // ==================== MIGRATION CONVENIENCE METHODS ====================

    public Map<String, Object> getInternalState() {
        return this.config.getInternalState();
    }

    /**
     * Gets the raw value at the specified key path (alias for {@link #getRaw}).
     *
     * @param key the dot-separated key path
     * @return the raw value, or null if not found
     */
    public Object get(@NonNull String key) {
        return this.getRaw(key);
    }

    public boolean existsRaw(@NonNull String key) {
        return this.valueExists(this.config.getInternalState(), key);
    }

    /**
     * Checks if a key exists at the specified path (alias for {@link #existsRaw}).
     *
     * @param key the dot-separated key path
     * @return true if the key exists
     */
    public boolean exists(@NonNull String key) {
        return this.existsRaw(key);
    }

    public Object removeRaw(@NonNull String key) {
        return this.valueRemove(this.config.getInternalState(), key);
    }

    /**
     * Removes the value at the specified key path.
     *
     * @param key the dot-separated key path
     * @return the previous value, or null
     */
    public Object remove(@NonNull String key) {
        final Object old = this.removeRaw(key);
        this.config.update();
        return old;
    }

    // ==================== NESTED PATH HELPERS ====================

    protected boolean valueExists(Map<?, ?> document, String path) {
        String[] split = path.split(this.nestedSeparator);
        for (int i = 0; i < split.length; i++) {
            String part = split[i];
            if (i == (split.length - 1)) {
                return document.containsKey(part);
            }
            Object element = document.get(part);
            if (element instanceof Map) {
                document = (Map<?, ?>) element;
                continue;
            }
            return false;
        }
        return false;
    }

    protected Object valueExtract(Map<?, ?> document, String path) {
        String[] split = path.split(this.nestedSeparator);
        for (int i = 0; i < split.length; i++) {
            String part = split[i];
            Object element = document.get(part);
            if (i == (split.length - 1)) {
                return element;
            }
            if (element instanceof Map) {
                document = (Map<?, ?>) element;
                continue;
            }
            // can't traverse deeper - return null
            return null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected Object valuePut(Map<?, ?> document, String path, Object value) {
        String[] split = path.split(this.nestedSeparator);
        Map<Object, Object> current = (Map<Object, Object>) document;
        for (int i = 0; i < split.length; i++) {
            String part = split[i];
            if (i == (split.length - 1)) {
                return current.put(part, value);
            }
            Object element = current.get(part);
            if (element instanceof Map) {
                current = (Map<Object, Object>) element;
                continue;
            }
            if (element != null) {
                String elementStr = element.getClass().getSimpleName();
                throw new IllegalArgumentException("Cannot insert '" + path + "': " +
                    "type conflict (ended at index " + i + " [" + part + ":" + elementStr + "])");
            }
            Map<Object, Object> map = new LinkedHashMap<>();
            current.put(part, map);
            current = map;
        }
        throw new IllegalArgumentException("Cannot put '" + path + "'");
    }

    @SuppressWarnings("unchecked")
    protected Object valueRemove(Map<?, ?> document, String path) {
        String[] split = path.split(this.nestedSeparator);
        Map<Object, Object> current = (Map<Object, Object>) document;
        for (int i = 0; i < split.length; i++) {
            String part = split[i];
            if (i == (split.length - 1)) {
                return current.remove(part);
            }
            Object element = current.get(part);
            if (element instanceof Map) {
                current = (Map<Object, Object>) element;
                continue;
            }
            return null;
        }
        return null;
    }
}
