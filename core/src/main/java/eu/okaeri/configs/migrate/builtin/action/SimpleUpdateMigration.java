package eu.okaeri.configs.migrate.builtin.action;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.migrate.ConfigMigration;
import eu.okaeri.configs.migrate.view.RawConfigView;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Objects;
import java.util.function.Function;

@ToString
@RequiredArgsConstructor
public class SimpleUpdateMigration implements ConfigMigration {

    private final String key;
    private final Function<Object, Object> function;

    @Override
    public boolean runMigration(@NonNull OkaeriConfig config, @NonNull RawConfigView view) {
        Object oldValue = view.getRaw(this.key);
        Object newValue = this.function.apply(oldValue);
        view.setRaw(this.key, newValue);
        return !Objects.equals(oldValue, newValue);
    }
}
