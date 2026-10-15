package inceris.custommusic.mixin;

import com.cobblemon.mod.common.client.CobblemonClient;
import inceris.custommusic.CustomBattleMusicClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CobblemonClient.class, remap = false)
public abstract class CobblemonClientMixin {
  @Inject(method = "endBattle", at = @At("HEAD"), remap = false)
  private void custommusic$clearBattle(CallbackInfo callback) {
    CustomBattleMusicClient.onBattleEnded();
  }
}
