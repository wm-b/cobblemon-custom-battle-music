package inceris.custommusic.battle;

import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.client.Minecraft;

public final class TrainerIdentityResolver {
    public TrainerIdentity resolve(ClientBattleActor actor, Minecraft client) {
        String id = null;
        if (client.level != null) {
            for (var entity : client.level.entitiesForRendering()) {
                if (entity.getUUID().equals(actor.getUuid())) {
                    // NPC_CLASS identifies the definition; RESOURCE_IDENTIFIER can be overridden for rendering.
                    if (entity instanceof NPCEntity npc) id = npc.getEntityData().get(NPCEntity.Companion.getNPC_CLASS()).toString();
                    break;
                }
            }
        }
        return new TrainerIdentity(id, actor.getDisplayName().getString());
    }
}
