package inceris.custommusic.audio

import com.cobblemon.mod.common.client.sound.BattleMusicController
import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

object CobblemonMusicAdapter {
    fun isAvailable(sound: ResourceLocation, client: Minecraft): Boolean {
        val definition = client.soundManager.getSoundEvent(sound) ?: return false
        return definition.weight > 0
    }

    fun switchTo(sound: ResourceLocation, client: Minecraft, looping: Boolean = true) {
        val current = BattleMusicController.music
        if (current.isFading()) return
        val active = client.soundManager.isActive(current)
        if (active && current.location == sound && current.isLooping == looping) return
        if (!isAvailable(sound, client)) return
        val next = BattleMusicInstance(SoundEvent.createVariableRangeEvent(sound), current.volume, current.pitch)
        // Resource reload stops sound instances. Restore battle music through the native controller.
        if (active) BattleMusicController.switchMusic(next) else BattleMusicController.initializeMusic(next)
    }

    fun end() = BattleMusicController.endMusic()
}
