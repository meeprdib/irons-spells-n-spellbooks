package io.redspace.ironsspellbooks.entity.mobs.dead_king_boss;

import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber
public class DeadKingMusicManager {
    @Nullable
    private static DeadKingMusicManager INSTANCE;
    static final SoundSource SOUND_SOURCE = SoundSource.RECORDS;
    static final int FIRST_PHASE_MELODY_LENGTH_MILIS = 28790;
    static final int INTRO_LENGTH_MILIS = 17600;

    DeadKingBoss boss;
    final SoundManager soundManager;
    FadeableSoundInstance beginSound;
    FadeableSoundInstance firstPhaseMelody;
    FadeableSoundInstance firstPhaseAccent;
    FadeableSoundInstance secondPhaseMelody;
    FadeableSoundInstance transitionMusic;
    Set<FadeableSoundInstance> layers = new HashSet<>();
    private int accentStage = 0;
    private long lastMilisPlayed;
    private boolean hasPlayedIntro;
    DeadKingBoss.Phases stage;
    boolean done = false;
    boolean finishing = false;

    private DeadKingMusicManager(DeadKingBoss boss) {
        this.boss = boss;
        this.soundManager = Minecraft.getInstance().getSoundManager();
        stage = DeadKingBoss.Phases.values()[boss.getPhase()];
        beginSound = new FadeableSoundInstance(SoundRegistry.DEAD_KING_MUSIC_INTRO.get(), SOUND_SOURCE, false);
        firstPhaseMelody = new FadeableSoundInstance(SoundRegistry.DEAD_KING_FIRST_PHASE_MELODY.get(), SOUND_SOURCE, true);
        firstPhaseAccent = new FadeableSoundInstance(SoundRegistry.DEAD_KING_FIRST_PHASE_ACCENT_01.get(), SOUND_SOURCE, false);
        secondPhaseMelody = new FadeableSoundInstance(SoundRegistry.DEAD_KING_SECOND_PHASE_MELODY_ALT.get(), SOUND_SOURCE, true);
        transitionMusic = new FadeableSoundInstance(SoundRegistry.DEAD_KING_SUSPENSE.get(), SOUND_SOURCE, false);
        init();
    }

    private void init() {
        soundManager.stop(null, SoundSource.MUSIC);
        switch (stage) {
            case FirstPhase -> {
                addLayer(beginSound);
                lastMilisPlayed = System.currentTimeMillis();
            }
            case FinalPhase -> initSecondPhase();
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (INSTANCE != null && event.phase == TickEvent.Phase.START) {
            INSTANCE.tick();
        }
    }

    public static void createOrResumeInstance(DeadKingBoss boss) {
        if (INSTANCE == null || INSTANCE.done) {
            INSTANCE = new DeadKingMusicManager(boss);
        } else {
            INSTANCE.triggerResume(boss);
        }
    }

    public static void stop(DeadKingBoss boss) {
        if (INSTANCE != null && INSTANCE.boss.getUUID().equals(boss.getUUID())) {
            INSTANCE.triggerStop();
            INSTANCE.finishing = true;
        }
    }

    private void tick() {
        if (done) {
            return;
        } else if (finishing) {
            done = checkDone();
            return;
        }
        if (boss.isDeadOrDying()) {
            triggerStop();
            finishing = true;
            return;
        }
        var bossPhase = DeadKingBoss.Phases.values()[boss.getPhase()];
        switch (bossPhase) {
            case FirstPhase -> {
                if (!hasPlayedIntro) {
                    //soundManager.isActive() seems to be delayed, so we do additional ms check
                    if (!soundManager.isActive(beginSound) || lastMilisPlayed + INTRO_LENGTH_MILIS < System.currentTimeMillis()) {
                        hasPlayedIntro = true;
                        initFirstPhase();
                    }
                } else if (lastMilisPlayed + FIRST_PHASE_MELODY_LENGTH_MILIS * 2 < System.currentTimeMillis()) {
                    //play accent every other time
                    playAccent(firstPhaseAccent);
                }
            }
            case Transitioning -> {
                if (stage != DeadKingBoss.Phases.Transitioning) {
                    stage = DeadKingBoss.Phases.Transitioning;
                    triggerStop();
                    addLayer(transitionMusic);
                }
            }
            case FinalPhase -> {
                if (stage != DeadKingBoss.Phases.FinalPhase) {
                    stage = DeadKingBoss.Phases.FinalPhase;
                    initSecondPhase();
                }
            }
        }
    }

    /**
     * Returns true if instance is done (completely over)
     */

    private boolean checkDone() {
        for (FadeableSoundInstance soundInstance : layers) {
            if (!soundInstance.isStopped() && soundManager.isActive(soundInstance)) {
                return false;
            }
        }
        return true;
    }

    private void addLayer(FadeableSoundInstance soundInstance) {
        layers.stream().filter((sound) -> sound.isStopped() || !soundManager.isActive(sound)).toList().forEach(layers::remove);
        soundManager.play(soundInstance);
        layers.add(soundInstance);
    }

    private void playAccent(FadeableSoundInstance soundInstance) {
        accentStage++;
        lastMilisPlayed = System.currentTimeMillis();
        addLayer(soundInstance);
    }

    public void triggerStop() {
        layers.forEach(FadeableSoundInstance::triggerStop);
    }

    public void triggerResume(DeadKingBoss boss) {
        if (boss.getUUID().equals(this.boss.getUUID())) {
            //Object reference could have changed, update it if it is the same entity
            this.boss = boss;
        }
        layers.forEach((sound) -> {
            sound.triggerStart();
            if (!soundManager.isActive(sound)) {
                soundManager.play(sound);
            }
        });
        finishing = false;
    }

    private void initFirstPhase() {
        accentStage = 0;
        addLayer(firstPhaseMelody);
        playAccent(firstPhaseAccent);
    }

    private void initSecondPhase() {
        accentStage = 0;
        addLayer(secondPhaseMelody);
    }
}
