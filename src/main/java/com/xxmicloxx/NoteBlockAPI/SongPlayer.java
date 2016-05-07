package com.xxmicloxx.NoteBlockAPI;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class SongPlayer {

    protected Song song;
    protected boolean playing = false;
    protected short tick = -1;
    protected Set<String> playerList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    protected boolean autoDestroy = false;
    protected boolean destroyed = false;
    protected Thread playerThread;
    protected byte fadeTarget = 100;
    protected byte volume = 100;
    protected byte fadeStart = volume;
    protected int fadeDuration = 60;
    protected int fadeDone = 0;
    protected FadeType fadeType = FadeType.FADE_LINEAR;

    public SongPlayer(Song song) {
        this.song = song;
        createThread();
    }

    public FadeType getFadeType() {
        return fadeType;
    }

    public void setFadeType(FadeType fadeType) {
        this.fadeType = fadeType;
    }

    public byte getFadeTarget() {
        return fadeTarget;
    }

    public void setFadeTarget(byte fadeTarget) {
        this.fadeTarget = fadeTarget;
    }

    public byte getFadeStart() {
        return fadeStart;
    }

    public void setFadeStart(byte fadeStart) {
        this.fadeStart = fadeStart;
    }

    public int getFadeDuration() {
        return fadeDuration;
    }

    public void setFadeDuration(int fadeDuration) {
        this.fadeDuration = fadeDuration;
    }

    public int getFadeDone() {
        return fadeDone;
    }

    public void setFadeDone(int fadeDone) {
        this.fadeDone = fadeDone;
    }

    protected void calculateFade() {
        if (fadeDone == fadeDuration) {
            return; // no fade today
        }
        double targetVolume = Interpolator.interpLinear(new double[]{0, fadeStart, fadeDuration, fadeTarget}, fadeDone);
        setVolume((byte) targetVolume);
        fadeDone++;
    }

    protected void createThread() {
        playerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!destroyed) {
                    long startTime = System.currentTimeMillis();
                    if (playing) {
                        calculateFade();
                        tick++;
                        if (tick > song.getLength()) {
                            playing = false;
                            tick = -1;
                            SongEndEvent event = new SongEndEvent(SongPlayer.this);
                            Bukkit.getPluginManager().callEvent(event);
                            if (autoDestroy) {
                                destroy();
                                return;
                            }
                        }
                        for (String s : playerList) {
                            Player p = Bukkit.getPlayerExact(s);
                            if (p == null) {
                                // offline...
                                continue;
                            }
                            playTick(p, tick);
                        }
                    }
                    long duration = System.currentTimeMillis() - startTime;
                    float delayMillis = song.getDelay() * 50;
                    if (duration < delayMillis) {
                        try {
                            Thread.sleep((long) (delayMillis - duration));
                        } catch (InterruptedException e) {
                            // do nothing
                        }
                    }
                }
            }
        });
        playerThread.setPriority(Thread.MAX_PRIORITY);
        playerThread.start();
    }

    public Set<String> getPlayerList() {
        return Collections.unmodifiableSet(playerList);
    }

    public void addPlayer(Player p) {
        if (!playerList.contains(p.getName())) {
            playerList.add(p.getName());
            ArrayList<SongPlayer> songs = NoteBlockPlayerMain.plugin.playingSongs
                    .get(p.getName());
            if (songs == null) {
                songs = new ArrayList<SongPlayer>();
            }
            songs.add(this);
            NoteBlockPlayerMain.plugin.playingSongs.put(p.getName(), songs);
        }
    }

    public boolean getAutoDestroy() {
        return autoDestroy;
    }

    public void setAutoDestroy(boolean value) {
        autoDestroy = value;
    }

    public abstract void playTick(Player p, int tick);

    public void destroy() {
        SongDestroyingEvent event = new SongDestroyingEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        //Bukkit.getScheduler().cancelTask(threadId);
        if (event.isCancelled()) {
            return;
        }
        destroyed = true;
        playing = false;
        setTick((short) -1);
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
        if (!playing) {
            SongStoppedEvent event = new SongStoppedEvent(this);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    public short getTick() {
        return tick;
    }

    public void setTick(short tick) {
        this.tick = tick;
    }

    public void removePlayer(Player p) {
        playerList.remove(p.getName());
        if (NoteBlockPlayerMain.plugin.playingSongs.get(p.getName()) == null) {
            return;
        }
        ArrayList<SongPlayer> songs = new ArrayList<SongPlayer>(
                NoteBlockPlayerMain.plugin.playingSongs.get(p.getName()));
        songs.remove(this);
        NoteBlockPlayerMain.plugin.playingSongs.put(p.getName(), songs);
        if (playerList.isEmpty() && autoDestroy) {
            SongEndEvent event = new SongEndEvent(this);
            Bukkit.getPluginManager().callEvent(event);
            destroy();
        }
    }

    public byte getVolume() {
        return volume;
    }

    public void setVolume(byte volume) {
        this.volume = volume;
    }

    public Song getSong() {
        return song;
    }
}
