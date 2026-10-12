package inceris.custommusic.battle

import com.cobblemon.mod.common.client.battle.ClientBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.minecraft.client.Minecraft

class TrainerIdentityResolver {
    fun resolve(actor: ClientBattleActor, client: Minecraft): TrainerIdentity {
        val entity = client.level?.entitiesForRendering()?.firstOrNull { it.uuid == actor.uuid } as? NPCEntity
        // NPC_CLASS identifies the trainer definition. RESOURCE_IDENTIFIER can be overridden for rendering.
        val id = entity?.entityData?.get(NPCEntity.NPC_CLASS)?.toString()
        return TrainerIdentity(id, actor.displayName.string)
    }
}
