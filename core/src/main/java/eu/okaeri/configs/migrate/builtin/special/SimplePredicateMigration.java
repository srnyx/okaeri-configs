package eu.okaeri.configs.migrate.builtin.special;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.migrate.ConfigMigration;
import eu.okaeri.configs.migrate.view.RawConfigView;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.function.Predicate;

@ToString
@RequiredArgsConstructor
public class SimplePredicateMigration<T> implements ConfigMigration {

    private final String key;
    private final Predicate<T> predicate;

    @Override
    @SuppressWarnings("unchecked")
    public boolean runMigration(@NonNull OkaeriConfig config, @NonNull RawConfigView view) {

        if (!view.existsRaw(this.key)) {
            return false;
        }

        T value = (T) view.getRaw(this.key);
        return this.predicate.test(value);
    }
}
