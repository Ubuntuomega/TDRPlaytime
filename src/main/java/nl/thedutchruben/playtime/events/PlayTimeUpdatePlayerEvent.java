package nl.thedutchruben.playtime.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayTimeUpdatePlayerEvent extends Event {
    private Player player;
    private long oldTime,newTime;

    /**
     * The default constructor is defined for cleaner code. This constructor
     * assumes the event is synchronous.
     */
    public PlayTimeUpdatePlayerEvent(Player player, long oldTime, long newTime) {
        super(true);
        this.player = player;
        this.oldTime = oldTime;
        this.newTime = newTime;
    }



    public long getNewTime() {
        return newTime;
    }

    public long getOldTime() {
        return oldTime;
    }

    public Player getPlayer() {
        return player;
    }

    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
