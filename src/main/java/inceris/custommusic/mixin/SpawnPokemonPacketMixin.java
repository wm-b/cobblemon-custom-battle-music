package inceris.custommusic.mixin;

import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import inceris.custommusic.CustomBattleMusicClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SpawnPokemonPacket.class, remap = false)
public abstract class SpawnPokemonPacketMixin {
    // The typed method and its bridge may both notify; pending records are keyed by Pokemon UUID.
    @Inject(method = "applyData", at = @At("RETURN"), remap = false)
    private void custommusic$verifiedAppearance(CallbackInfo callback) {
        CustomBattleMusicClient.onPokemonSpawn((SpawnPokemonPacket) (Object) this);
    }
}
