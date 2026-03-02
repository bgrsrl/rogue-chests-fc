package com.roguechests;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.Skill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Rogue Chests FC",
	description = "Monitors Rogue Chests friends chat for members with thieving below 84",
	tags = {"rogue", "chests", "thieving", "friends chat", "overlay", "alert"}
)
public class RogueChestsPlugin extends Plugin
{
	// The owner/name of the friends chat to monitor
	private static final String FC_OWNER = "Rogue chests";
	private static final int THIEVING_THRESHOLD = 84;
	// Sound effect ID 4039 = GE buy/increment ding — a clear, noticeable alert
	private static final int ALERT_SOUND = 4039;

	// InterfaceID.ChatchannelCurrent.LIST = 458764 → group=7, child=12
	private static final int FC_LIST_GROUP = 7;
	private static final int FC_LIST_CHILD = 12;

	// Red color for low-level member names in the FC list
	private static final int COLOR_RED = 0xFF0000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private HiscoreClient hiscoreClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RogueChestsOverlay overlay;

	// Single-threaded so hiscore lookups are queued and don't hammer the API
	private ExecutorService executor;

	// Names of FC members confirmed to have thieving < 84 (shown in overlay)
	private final Set<String> lowLevelMembers = ConcurrentHashMap.newKeySet();

	// Names currently being looked up — prevents duplicate in-flight requests
	private final Set<String> pendingLookups = ConcurrentHashMap.newKeySet();

	@Override
	protected void startUp()
	{
		executor = Executors.newSingleThreadExecutor();
		overlayManager.add(overlay);
		// If the player is already in the FC when the plugin loads, scan existing members
		scanCurrentMembers();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		executor.shutdownNow();
		lowLevelMembers.clear();
		pendingLookups.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING)
		{
			lowLevelMembers.clear();
			pendingLookups.clear();
		}
	}

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event)
	{
		// Clear state whenever the FC changes (join or leave)
		lowLevelMembers.clear();
		pendingLookups.clear();

		if (event.isJoined())
		{
			// Just joined — scan everyone already in the chat
			scanCurrentMembers();
		}
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined event)
	{
		if (!isInRogueChestsFc())
		{
			return;
		}
		String name = Text.sanitize(event.getMember().getName());
		// Play the sound alert for join events (true = play sound)
		lookupMember(name, true);
	}

	@Subscribe
	public void onFriendsChatMemberLeft(FriendsChatMemberLeft event)
	{
		String name = Text.sanitize(event.getMember().getName());
		lowLevelMembers.remove(name);
		pendingLookups.remove(name);
	}

	// Fired by the game whenever the FC member list is rebuilt/scrolled
	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.FRIENDS_CHAT_CHANNEL_REBUILD)
		{
			colorFcList();
		}
	}

	boolean isInRogueChestsFc()
	{
		FriendsChatManager fcm = client.getFriendsChatManager();
		if (fcm == null)
		{
			return false;
		}
		String owner = Text.sanitize(fcm.getOwner());
		return FC_OWNER.equalsIgnoreCase(owner);
	}

	Set<String> getLowLevelMembers()
	{
		return lowLevelMembers;
	}

	// Walk the FC list widget and color low-level member names red.
	// Called on script rebuild AND immediately when a new low-level member is confirmed.
	void colorFcList()
	{
		Widget list = client.getWidget(FC_LIST_GROUP, FC_LIST_CHILD);
		if (list == null || list.isHidden())
		{
			return;
		}

		Widget[] rows = list.getDynamicChildren();
		if (rows == null)
		{
			return;
		}

		for (Widget row : rows)
		{
			// Each row may be a container — check its dynamic children for the name text
			Widget[] cols = row.getDynamicChildren();
			if (cols != null)
			{
				for (Widget col : cols)
				{
					applyColorIfLowLevel(col);
				}
			}
			else
			{
				// Fallback: row itself might be the text widget
				applyColorIfLowLevel(row);
			}
		}
	}

	private void applyColorIfLowLevel(Widget widget)
	{
		String text = widget.getText();
		if (text == null || text.isEmpty())
		{
			return;
		}
		// Strip any color tags the game may have embedded in the name
		String name = Text.sanitize(text);
		if (lowLevelMembers.contains(name))
		{
			widget.setTextColor(COLOR_RED);
		}
	}

	private void scanCurrentMembers()
	{
		if (!isInRogueChestsFc())
		{
			return;
		}
		FriendsChatManager fcm = client.getFriendsChatManager();
		if (fcm == null)
		{
			return;
		}
		// Scan existing members without triggering the sound alert (false = no sound)
		for (FriendsChatMember member : fcm.getMembers())
		{
			String name = Text.sanitize(member.getName());
			lookupMember(name, false);
		}
	}

	private void lookupMember(String name, boolean playSound)
	{
		if (name == null || name.isEmpty())
		{
			return;
		}
		// Skip if a lookup is already running for this name
		if (!pendingLookups.add(name))
		{
			return;
		}
		executor.submit(() ->
		{
			try
			{
				HiscoreResult result = hiscoreClient.lookup(name, HiscoreEndpoint.NORMAL);
				if (result == null)
				{
					return;
				}
				Skill thieving = result.getSkill(HiscoreSkill.THIEVING);
				if (thieving == null)
				{
					return;
				}
				int level = thieving.getLevel();
				// level == -1 means not ranked/private — skip to avoid false positives
				if (level > 0 && level < THIEVING_THRESHOLD)
				{
					boolean isNew = lowLevelMembers.add(name);
					if (isNew)
					{
						clientThread.invokeLater(() ->
						{
							// Recolor the FC list immediately so names turn red right away
							colorFcList();
							if (playSound)
							{
								client.playSoundEffect(ALERT_SOUND);
							}
						});
					}
				}
			}
			catch (IOException e)
			{
				log.debug("Hiscore lookup failed for {}: {}", name, e.getMessage());
			}
			finally
			{
				pendingLookups.remove(name);
			}
		});
	}
}
