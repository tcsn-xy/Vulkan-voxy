package me.cortex.voxy.common.world;
/** A refresh may supersede a prior session's cache, never this session's live observation. */
public final class ImportUpdatePolicy {
    private ImportUpdatePolicy(){}
    public static boolean accepts(boolean sessionLive,boolean recorded,boolean refreshCurrent){return !sessionLive&&(refreshCurrent||!recorded);}
}
