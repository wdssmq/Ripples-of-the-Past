package com.github.standobyte.jojo.entity.mob.rps;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.util.utils.JojoModUtil;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;

public class RPSPvpGamesMap {
    private final Map<PlayersPair, RockPaperScissorsGame> pvpGames = new HashMap<>();
    
    
    public RockPaperScissorsGame addGame(RockPaperScissorsGame game) {
        pvpGames.put(new PlayersPair(game.player1.getEntityUuid(), game.player2.getEntityUuid()), game);
        return game;
    }
    
    @Nullable
    public RockPaperScissorsGame getGameBetween(PlayerEntity player1, PlayerEntity player2) {
        return pvpGames.get(new PlayersPair(player1.getUUID(), player2.getUUID()));
    }
    
    public RockPaperScissorsGame getOrCreateGame(PlayerEntity player1, PlayerEntity player2) {
        RockPaperScissorsGame unfinishedGame = getGameBetween(player1, player2);
        return unfinishedGame != null && !unfinishedGame.isGameOver() ? unfinishedGame : addGame(new RockPaperScissorsGame(player1, player2));
    }
    
    public CompoundNBT save() {
        CompoundNBT nbt = new CompoundNBT();
        int i = 0;
        Iterator<Map.Entry<PlayersPair, RockPaperScissorsGame>> it = pvpGames.entrySet().iterator();
        while (it.hasNext()) {
            nbt.put(String.valueOf(i++), it.next().getValue().writeNBT());
        }
        nbt.putInt("Size", i);
        return nbt;
    }
    
    public void load(CompoundNBT nbt) {
        int size = nbt.getInt("Size");
        for (int i = 0; i < size; i++) {
            if (nbt.contains(String.valueOf(i), JojoModUtil.getNbtId(CompoundNBT.class))) {
                RockPaperScissorsGame game = RockPaperScissorsGame.fromNBT(nbt.getCompound(String.valueOf(i)));
                if (game != null) {
                    addGame(game);
                }
            }
        }
    }
    
    
    
    private static class PlayersPair {
        private final UUID player1ID;
        private final UUID player2ID;
        
        private PlayersPair(UUID player1ID, UUID player2ID) {
            this.player1ID = player1ID;
            this.player2ID = player2ID;
        }
        
        @Override
        public boolean equals(Object object) {
            if (super.equals(object)) return true;
            if (object instanceof PlayersPair) {
                PlayersPair other = (PlayersPair) object;
                return this.player1ID.equals(other.player1ID) && this.player2ID.equals(other.player2ID)
                        || this.player1ID.equals(other.player2ID) && this.player2ID.equals(other.player1ID);
            }
            return false;
        }
        
        @Override
        public int hashCode() {
            return player1ID.hashCode() / 2 + player2ID.hashCode() / 2;
        }
    }

}
