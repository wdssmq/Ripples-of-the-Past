package com.github.standobyte.jojo.client.sound;

import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.TickableSound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

public class StandOstSound extends TickableSound implements ITickableSound {
    private int fadeAwayTicks = -1;
    private int fadeAwayInitialTicks = -1;
    
    private final GameSettings options;
    private final float musicVolume;

    public StandOstSound(SoundEvent sound, Minecraft mc) {
        super(sound, SoundCategory.RECORDS);
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.looping = false;
        this.delay = 0;
        this.attenuation = ISound.AttenuationType.NONE;
        this.relative = true;
        
        this.options = mc.options;
        this.musicVolume = options.getSoundSourceVolume(SoundCategory.MUSIC);
        this.options.setSoundCategoryVolume(SoundCategory.MUSIC, 0);
    }

    @Override
    public void tick() {
        if (!isStopped()) {
            if (fadeAwayInitialTicks > -1 && fadeAwayTicks > 0) {
                volume = (float) fadeAwayTicks-- / (float) fadeAwayInitialTicks;
            }
            if (fadeAwayTicks == 0) {
                stopOst();
            }
        }
    }
    
    private void stopOst() {
        stop();
        options.setSoundCategoryVolume(SoundCategory.MUSIC, musicVolume);
    }
    
    public void setFadeAway(int ticks) {
        if (ticks > -1 && this.fadeAwayInitialTicks == -1) {
            this.fadeAwayTicks = ticks;
            this.fadeAwayInitialTicks = ticks;
        }
    }
}
