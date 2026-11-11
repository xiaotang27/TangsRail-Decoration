package top.xiaotang27.trdm.ticket;

/** TRDM 票务系统采用的时间依据。 */
public enum TimeBasis {
    /** 现实系统时间。 */
    SYSTEM,
    /** Minecraft 游戏内时间；一天按 24000 tick 映射为票务的一天。 */
    GAME
}
