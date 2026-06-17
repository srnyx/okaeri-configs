package eu.okaeri.configs.migrate;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.migrate.view.RawConfigView;
import lombok.NonNull;

@FunctionalInterface
public interface ConfigMigration {

    /**
     * @param config if you update the config directly, you MUST call {@link OkaeriConfig#updateInternalState()} after the migration
     */
    boolean runMigration(@NonNull OkaeriConfig config, @NonNull RawConfigView view);

    default boolean migrate(@NonNull OkaeriConfig config, @NonNull RawConfigView view, boolean update) {
        final boolean result = this.runMigration(config, view);
        if (update) config.update();
        return result;
    }

    default boolean migrate(@NonNull OkaeriConfig config, @NonNull RawConfigView view) {
        return this.migrate(config, view, true);
    }
}
