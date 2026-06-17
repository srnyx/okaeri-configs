package eu.okaeri.configs.migrate.builtin.action;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.migrate.ConfigMigration;
import eu.okaeri.configs.migrate.view.RawConfigView;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Objects;

@ToString
@RequiredArgsConstructor
public class SimpleCopyMigration implements ConfigMigration {

    private final String fromKey;
    private final String toKey;

    @Override
    public boolean runMigration(@NonNull OkaeriConfig config, @NonNull RawConfigView view) {

        if (!view.existsRaw(this.fromKey)) {
            return false;
        }

        Object targetValue = view.getRaw(this.fromKey);
        Object oldValue = view.setRaw(this.toKey, targetValue);

        return !Objects.equals(targetValue, oldValue);
    }
}
