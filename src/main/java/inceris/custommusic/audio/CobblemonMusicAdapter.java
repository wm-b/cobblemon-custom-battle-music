package inceris.custommusic.audio;

import com.cobblemon.mod.common.client.sound.BattleMusicController;
import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class CobblemonMusicAdapter {
  private CobblemonMusicAdapter() {}

  public static boolean isAvailable(ResourceLocation sound, Minecraft client) {
    var definition = client.getSoundManager().getSoundEvent(sound);
    return definition != null && definition.getWeight() > 0;
  }

  public static void switchTo(ResourceLocation sound, Minecraft client, boolean looping) {
    var controller = BattleMusicController.INSTANCE;
    var current = controller.getMusic();
    if (current.isFading()) return;
    boolean active = client.getSoundManager().isActive(current);
    if (active && current.getLocation().equals(sound) && current.isLooping() == looping) return;
    if (!isAvailable(sound, client)) return;
    var next =
        new BattleMusicInstance(
            SoundEvent.createVariableRangeEvent(sound), current.getVolume(), current.getPitch());
    // Resource reload stops sound instances. Restore music through the native controller.
    if (active) controller.switchMusic(next);
    else controller.initializeMusic(next);
  }

  public static void end() {
    BattleMusicController.INSTANCE.endMusic();
  }
}
