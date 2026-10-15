package inceris.custommusic.audio;

public record ProgramTrack(String sound, MusicStage stage) {
    public boolean looping() { return stage != MusicStage.INTRO; }
}
