package inceris.custommusic.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleMusicHandler;
import inceris.custommusic.CustomBattleMusicClient;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleMusicHandler.class, remap = false)
public abstract class BattleMusicHandlerMixin {
    // Only the typed handler invokes this factory; the Kotlin bridge does not.
    // Cobblemon method names are unmapped, while this Minecraft invocation must be remapped.
    @ModifyArg(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/sounds/SoundEvent;createVariableRangeEvent(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/sounds/SoundEvent;",
            remap = true), index = 0, remap = false)
    private ResourceLocation custommusic$resolveSound(ResourceLocation original) {
        return CustomBattleMusicClient.resolveMusic(original);
    }

    @Inject(method = "handle", at = @At("RETURN"), remap = false)
    private void custommusic$afterMusic(CallbackInfo callback) {
        CustomBattleMusicClient.onMusicHandled();
    }
}
