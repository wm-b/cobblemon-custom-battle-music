package inceris.custommusic.mixin;

import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance;
import inceris.custommusic.CustomBattleMusicClient;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleMusicInstance.class, remap = false)
public abstract class BattleMusicInstanceMixin extends SimpleSoundInstance {
    protected BattleMusicInstanceMixin(SoundEvent sound, SoundSource source, float volume, float pitch,
                                      RandomSource random, double x, double y, double z) {
        super(sound, source, volume, pitch, random, x, y, z);
    }
    // Runs before SoundManager.play reads looping, including instances made by the native packet handler.
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void custommusic$configureIntro(CallbackInfo callback) {
        if (CustomBattleMusicClient.isIntro(getLocation())) looping = false;
    }
}
