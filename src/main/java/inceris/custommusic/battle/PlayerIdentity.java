package inceris.custommusic.battle;

import java.util.UUID;

public record PlayerIdentity(UUID uuid, String username) {
    public PlayerIdentity(UUID uuid) { this(uuid, null); }
}
