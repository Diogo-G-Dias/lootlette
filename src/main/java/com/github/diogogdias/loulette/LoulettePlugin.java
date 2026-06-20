package com.github.diogogdias.loulette;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.awt.Color;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Lootlette",
	description = "Spins a slot-machine reel of a monster's drop table the moment its HP hits zero and lands on what you actually got",
	tags = {"loot", "drop", "slot", "roll", "reel", "roulette", "fun", "wiki"}
)
public class LoulettePlugin extends Plugin
{
	static final String CONFIG_GROUP = "loulette";
	static final int NOTHING_ID = -999;

	// Bosses whose HP bar empties at every phase transition, not just on death. The HP-zero pre-spin would
	// otherwise free-spin a reel mid-fight; these still roll on the actual kill via the loot event.
	private static final Set<String> MULTI_PHASE_BOSSES = new HashSet<>(Arrays.asList(
		"the nightmare", "phosani's nightmare", "verzik vitur", "kalphite queen"
	));

	// Raid room bosses whose rewards come from the raid chest, not the boss. Rolling per-boss just spins their
	// lore-book/journal "Always" unlocks (or nothing); the chest handles the real loot. Suppressed on both the
	// death and loot paths.
	private static final Set<String> RAID_CHEST_BOSSES = new HashSet<>(Arrays.asList(
		// Theatre of Blood
		"the maiden of sugadinti", "maiden of sugadinti", "pestilent bloat", "nylocas vasilias",
		"sotetseg", "xarpus", "verzik vitur",
		// Doom of Mokhaiotl — loot is collected from the post-kill burrow hole, not the boss corpse.
		"doom of mokhaiotl"
	));

	/**
	 * A raids reward chest: the reward-room chest object ids (to start the anticipation reel on entry and
	 * anchor it), the loot-event name prefix, the wiki drop-table page, and the unique ("purple") item set.
	 * A purple chest rolls only the uniques; a white chest rolls only the supplies.
	 */
	private static final class RaidChest
	{
		final Set<Integer> objectIds;
		final Set<String> objectNames; // lower-cased reward-room chest object names (multiloc-id safe)
		final String eventPrefix;
		final String page;
		final Set<String> uniqueNames; // lower-cased

		RaidChest(Set<Integer> objectIds, Set<String> objectNames, String eventPrefix, String page, String... uniques)
		{
			this.objectIds = objectIds;
			this.objectNames = objectNames;
			this.eventPrefix = eventPrefix;
			this.page = page;
			this.uniqueNames = new HashSet<>(Arrays.asList(uniques));
		}
	}

	private static final List<RaidChest> RAID_CHESTS = Arrays.asList(
		// Chambers of Xeric — no chest-spawn anticipation. Instead the reel starts on the Olm kill (raid-complete
		// message) and spins until the loot lands (see onChatMessage). Kept here for the unique set + loot event.
		new RaidChest(Collections.emptySet(), Collections.emptySet(),
			"Chambers of Xeric", "Ancient chest",
			"dexterous prayer scroll", "arcane prayer scroll", "twisted buckler", "dragon hunter crossbow",
			"dinh's bulwark", "ancestral hat", "ancestral robe top", "ancestral robe bottom", "dragon claws",
			"elder maul", "kodai insignia", "twisted bow", "olmlet", "twisted ancestral colour kit",
			"metamorphic dust"),
		// Theatre of Blood — Monumental chest. Match the PLAYER'S closed chest only (32992 regular, 32993 unique),
		// not teammates' (32990/32991) or the opened ids, so the reel anchors on your own chest. No name-match
		// (every party chest shares the name "Monumental chest").
		new RaidChest(new HashSet<>(Arrays.asList(32992, 32993)), Collections.emptySet(),
			"Theatre of Blood", "Monumental chest",
			"avernic defender hilt", "ghrazi rapier", "sanguinesti staff (uncharged)", "justiciar faceguard",
			"justiciar chestguard", "justiciar legguards", "scythe of vitur (uncharged)", "holy ornament kit",
			"sanguine ornament kit", "sanguine dust", "lil' zik"),
		// Tombs of Amascut — reward vault chest (closed 41696 + per-player closed variants 44787-44792). Name is
		// generic ("Chest"), so match by id only. 44786 is the opened state — excluded so the reel fires on entry.
		new RaidChest(new HashSet<>(Arrays.asList(41696, 44787, 44788, 44789, 44790, 44791, 44792)), Collections.emptySet(),
			"Tombs of Amascut", "Chest (Tombs of Amascut)",
			"osmumten's fang", "tumeken's shadow (uncharged)", "elidinis' ward", "lightbearer", "masori mask",
			"masori body", "masori chaps", "eye of the corruptor", "jewel of the sun", "breach of the scarab",
			"jewel of amascut", "tumeken's guardian"),
		// Doom of Mokhaiotl — the post-kill burrow hole glows gold (object 50940) only when a unique is rolled;
		// match that id alone so the anticipation reel of uniques appears just for golden holes (normal hole is 57285).
		new RaidChest(new HashSet<>(Arrays.asList(50940)), Collections.emptySet(),
			"Doom of Mokhaiotl", "Doom of Mokhaiotl",
			"avernic treads", "eye of ayak (uncharged)", "mokhaiotl cloth", "dom")
	);

	private static final int COINS_ITEM_ID = 995;
	private static final long ENGAGE_MS = 6000;
	// How long a reel free-spins waiting for its loot before giving up. Harvests can take 10s+ to yield, so the
	// spinner is also kept alive as long as the player keeps interacting with the creature (see onGameTick).
	private static final long PRESPIN_TIMEOUT_MS = 15000;
	// Safety cap on a persistent (CoX, Olm-kill) anticipation if the loot never lands (e.g. you leave the raid).
	private static final long PERSISTENT_ANTICIPATION_MS = 300000;
	private static final int MIN_POOL = 6;
	private static final int MIN_LAND_LOOPS = 1;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LouletteOverlay overlay;

	@Inject
	private LouletteReelOverlay reelOverlay;

	@Inject
	private DropTableService dropTableService;

	@Inject
	private LouletteConfig config;

	@Inject
	private ConfigManager configManager;

	// NPC names (lower-cased) the player has chosen to never roll a reel for. Persisted in the ignoredNpcs config.
	private final Set<String> ignoredNpcs = ConcurrentHashMap.newKeySet();

	private final List<ActiveRoll> activeRolls = new CopyOnWriteArrayList<>();
	private final Map<String, Set<Integer>> seenDrops = new ConcurrentHashMap<>();
	private final Set<Integer> globalSeen = ConcurrentHashMap.newKeySet();
	private final Map<String, ResolvedTable> tableCache = new ConcurrentHashMap<>();
	// Raids chest page -> resolved unique ("purple") item ids.
	private final Map<String, Set<Integer>> raidUniqueIds = new ConcurrentHashMap<>();
	private final Map<Integer, Long> engagedUntil = new ConcurrentHashMap<>();
	private final Set<Integer> handledDeaths = ConcurrentHashMap.newKeySet();
	// Dedupe: monster name (lower) -> game tick it was last rolled, so a kill isn't rolled twice when
	// both NpcLootReceived (ground) and ServerNpcLoot (server) fire for it.
	private final Map<String, Integer> handledLootTick = new ConcurrentHashMap<>();
	private final Random random = new Random();

	// CoX early switch: between Olm's head death and the chest loot, the "Valuable drop:" chat lines reveal the
	// loot ~3s early. Narrow the spinning reel the instant the first such line lands. 0 = still full pool,
	// 1 = narrowed to supplies (white), 2 = narrowed to uniques (purple); only ever progresses upward.
	private int coxNarrow;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(reelOverlay);
		loadIgnoredNpcs();
		log.debug("Lootlette started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(reelOverlay);
		activeRolls.clear();
		seenDrops.clear();
		globalSeen.clear();
		tableCache.clear();
		engagedUntil.clear();
		handledDeaths.clear();
		handledLootTick.clear();
		log.debug("Loulette stopped");
	}

	List<ActiveRoll> getActiveRolls()
	{
		return activeRolls;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Rarity colours are baked into each cached table's palette, and the rare-drop-table filter is baked
		// into its pool; rebuild cached tables on the next kill when either changes.
		if (CONFIG_GROUP.equals(event.getGroup()) && event.getKey() != null
			&& (event.getKey().startsWith("colour") || event.getKey().equals("hideRareDropTable")))
		{
			tableCache.clear();
		}
		if (CONFIG_GROUP.equals(event.getGroup()) && "ignoredNpcs".equals(event.getKey()))
		{
			loadIgnoredNpcs();
		}
	}

	private void loadIgnoredNpcs()
	{
		ignoredNpcs.clear();
		for (String s : config.ignoredNpcs().split(","))
		{
			final String name = s.trim().toLowerCase();
			if (!name.isEmpty())
			{
				ignoredNpcs.add(name);
			}
		}
	}

	private boolean isIgnored(String name)
	{
		return name != null && ignoredNpcs.contains(name.toLowerCase());
	}

	/** Toggle an NPC name on the ignore list, persist it, and drop any reel currently rolling for it. */
	private void setIgnored(String name, boolean ignore)
	{
		if (ignore)
		{
			ignoredNpcs.add(name.toLowerCase());
			activeRolls.removeIf(r -> name.equalsIgnoreCase(r.getMonster()));
		}
		else
		{
			ignoredNpcs.remove(name.toLowerCase());
		}
		configManager.setConfiguration(CONFIG_GROUP, "ignoredNpcs", String.join(",", ignoredNpcs));
		log.debug("{} '{}' (ignore list now {})", ignore ? "ignored" : "unignored", name, ignoredNpcs);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		final long now = System.currentTimeMillis();

		// Track who we're fighting so we only roll for our own kills.
		final Player me = client.getLocalPlayer();
		if (me != null && me.getInteracting() instanceof NPC)
		{
			final NPC target = (NPC) me.getInteracting();
			engagedUntil.put(target.getIndex(), now + ENGAGE_MS);
			// Warm the table while fighting so it's ready by the time it dies (instant for the bundled
			// snapshot; triggers the live fetch only when the fallback is enabled and the monster is unbundled).
			if (target.getName() != null)
			{
				dropTableService.table(target.getName());
			}
		}

		// Start the spin the instant one of our targets' HP hits zero.
		for (NPC npc : client.getNpcs())
		{
			if (npc == null || npc.getName() == null || !isDying(npc))
			{
				continue;
			}
			final int index = npc.getIndex();
			if (handledDeaths.contains(index) || !isEngaged(index, now))
			{
				continue;
			}
			handledDeaths.add(index);
			startSpin(npc, now);
		}


		// Keep a still-spinning reel alive while the player keeps interacting with its creature (long harvests).
		final int interactingIndex = me != null && me.getInteracting() instanceof NPC
			? ((NPC) me.getInteracting()).getIndex()
			: -1;
		activeRolls.removeIf(roll ->
		{
			if (roll.done(now, config.resultHoldMs()))
			{
				return true;
			}
			if (roll.isFinalised())
			{
				return false;
			}
			// A chest anticipation reel lingers until the chest is opened or 2x the result-hold time passes; a
			// persistent (Olm-kill) one spins until the loot lands, capped by a long safety timeout.
			if (roll.isAnticipation())
			{
				final long ttl = roll.isPersistent() ? PERSISTENT_ANTICIPATION_MS : 2L * config.resultHoldMs();
				return now > roll.getSpinStartMs() + ttl;
			}
			return now > roll.getSpinStartMs() + PRESPIN_TIMEOUT_MS && roll.getNpcIndex() != interactingIndex;
		});
		engagedUntil.values().removeIf(expiry -> expiry < now);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		handledDeaths.remove(event.getNpc().getIndex());
	}

	/** When the ignore toggle is on, add a Lootlette-ignore / -unignore option to NPC right-click menus. */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.rightClickIgnore())
		{
			return;
		}
		NPC npc = null;
		String target = null;
		for (MenuEntry entry : event.getMenuEntries())
		{
			if (entry.getNpc() != null && entry.getNpc().getName() != null)
			{
				npc = entry.getNpc();
				target = entry.getTarget();
				break;
			}
		}
		if (npc == null)
		{
			return;
		}
		final String name = npc.getName();
		final boolean ignored = isIgnored(name);
		client.createMenuEntry(1)
			.setOption(ignored ? "Lootlette-unignore" : "Lootlette-ignore")
			.setTarget(target)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> setIgnored(name, !ignored));
	}

	/** Clicking an NPC resolves its drop table ahead of time, so its pool and rarity colours are ready when it dies. */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		final MenuEntry entry = event.getMenuEntry();
		final NPC npc = entry.getNpc();
		if (npc == null || npc.getName() == null || tableCache.containsKey(npc.getName().toLowerCase()))
		{
			return;
		}
		final String monster = npc.getName();
		dropTableService.table(monster).thenAccept(entries ->
			clientThread.invoke(() -> resolveTable(monster, entries)));
	}

	/** HP hit zero: begin free-spinning a reel over the dying NPC's tile. */
	private void startSpin(NPC npc, long now)
	{
		final String monster = npc.getName();
		if (isIgnored(monster))
		{
			log.debug("skip spin '{}' (ignored)", monster);
			return;
		}
		// Chambers of Xeric: the Great Olm head dying is the kill. Start a persistent full-table reel here that
		// spins (top-centre) through the walk to the reward room and lands when the Ancient chest loot resolves.
		if ("Great Olm".equals(monster))
		{
			final RaidChest cox = raidByEventName("Chambers of Xeric");
			if (cox != null && !hasRollFor(cox.page))
			{
				log.debug("Great Olm HEAD died -> start CoX full-pool anticipation");
				coxNarrow = 0;
				startChestAnticipation(cox, npc.getWorldLocation(), true, true, true);
			}
			return;
		}
		// Inside Chambers of Xeric every room creature's loot is delivered by the Ancient chest, not the corpse, so
		// rolling per-kill either lands on the wrong (wiki) table or never lands at all (no loot event). Suppress
		// everything in here; only the Olm head (handled above) drives the chest anticipation.
		if (client.getVarbitValue(Varbits.IN_RAID) > 0)
		{
			log.debug("skip spin '{}' (inside CoX; loot comes from the chest)", monster);
			return;
		}
		if (isRaidChestBoss(monster))
		{
			log.debug("skip spin '{}' (raid boss; loot comes from the chest)", monster);
			return;
		}
		// Multi-phase bosses only reach here on the real death (isDying gates them to isDead), so no special-case
		// is needed — fall through and pre-spin like any other kill.
		if (!hasDropTable(monster))
		{
			log.debug("skip spin '{}' (no drop table)", monster);
			return;
		}
		ResolvedTable table = tableCache.get(monster.toLowerCase());
		if (table == null)
		{
			// Instant for bundled monsters; triggers the live fetch (if enabled) for unbundled ones.
			final List<DropEntry> entries = dropTableService.table(monster).getNow(null);
			if (entries != null && !entries.isEmpty())
			{
				table = resolveTable(monster, entries);
			}
		}
		// Nothing is rolled for a guaranteed-only drop table (e.g. ToB Maiden), so don't spin a reel for it.
		if (table != null && table.isAlwaysOnly())
		{
			log.debug("skip spin '{}' (only guaranteed drops)", monster);
			return;
		}
		final ActiveRoll roll = new ActiveRoll(npc.getIndex(), npc.getWorldLocation(), now, monster);
		roll.setPalette(table != null ? table.getPalette() : Collections.emptyMap());
		activeRolls.add(roll);

		// Always show a spinner during the death animation, even with no drop data yet.
		final List<Integer> symbols = new ArrayList<>(spinnerPool(monster));
		symbols.add(NOTHING_ID);
		padTo(symbols, MIN_POOL);
		Collections.shuffle(symbols, random);
		roll.getReels().add(new SlotReel(symbols, now, config.spinSpeed()));

		log.debug("startSpin '{}' (hp zero)", monster);
	}

	/**
	 * Strict drop-table check: only spin when we have evidence the NPC actually drops loot — either we've
	 * looted it before this session, or the bundled/live table is non-empty. NPCs with no drop table and
	 * tables not yet fetched are skipped; the loot event will still roll real drops.
	 */
	private boolean hasDropTable(String monster)
	{
		final String key = monster.toLowerCase();
		final Set<Integer> seen = seenDrops.get(key);
		if (seen != null && !seen.isEmpty())
		{
			return true;
		}
		final List<DropEntry> entries = dropTableService.table(monster).getNow(null);
		return entries != null && !entries.isEmpty();
	}

	private List<Integer> spinnerPool(String monster)
	{
		final ResolvedTable table = tableCache.get(monster.toLowerCase());
		if (table != null && !table.getPool().isEmpty())
		{
			return table.getPool();
		}
		final Set<Integer> seen = seenDrops.get(monster.toLowerCase());
		if (seen != null && !seen.isEmpty())
		{
			return new ArrayList<>(seen);
		}
		if (!globalSeen.isEmpty())
		{
			return new ArrayList<>(globalSeen);
		}
		return Collections.emptyList();
	}

	private boolean isDying(NPC npc)
	{
		// Multi-phase bosses (Nightmare, Verzik, KQ…) drop their HP bar to zero mid-fight — e.g. breaking the
		// Nightmare's shield reads as healthRatio==0 without a death. Count only the real death animation for
		// those, so a phase/shield break neither pre-spins a reel nor burns the one-shot death slot.
		final String name = npc.getName();
		if (name != null && MULTI_PHASE_BOSSES.contains(name.toLowerCase()))
		{
			return npc.isDead();
		}
		return npc.isDead() || (npc.getHealthScale() > 0 && npc.getHealthRatio() == 0);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		final NPC npc = event.getNpc();
		if (npc == null || npc.getName() == null || isRaidChestBoss(npc.getName()) || isIgnored(npc.getName()))
		{
			return;
		}
		final Collection<ItemStack> items = event.getItems();
		if (items == null || items.isEmpty())
		{
			return;
		}
		if (!claimLoot(npc.getName()))
		{
			return;
		}

		rollFor(npc.getIndex(), npc.getWorldLocation(), npc.getName(), new ArrayList<>(items), false);
	}

	/**
	 * Sailing creatures (and other server-tracked sources) are harvested, not killed, so they fire no death
	 * animation and no NpcLootReceived — only ServerNpcLoot. Roll for those here. Combat kills also fire this,
	 * but they're already owned by the death/NpcLootReceived path, so skip any creature with an active roll.
	 */
	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		final NPCComposition comp = event.getComposition();
		if (comp == null || comp.getName() == null)
		{
			return;
		}
		final String name = Text.removeTags(comp.getName());
		final Collection<ItemStack> items = event.getItems();
		log.debug("onServerNpcLoot '{}' id={} items={}", name, comp.getId(), items == null ? -1 : items.size());
		if (name.isEmpty() || "null".equals(name) || items == null || items.isEmpty()
			|| isRaidChestBoss(name) || isIgnored(name))
		{
			return;
		}
		// Dedupe against NpcLootReceived, which also fires for combat kills on the same tick.
		if (!claimLoot(name))
		{
			return;
		}

		// Anchor over the creature being harvested (the one we're interacting with, else nearest of that id).
		final Player me = client.getLocalPlayer();
		NPC npc = me != null && me.getInteracting() instanceof NPC ? (NPC) me.getInteracting() : null;
		if (npc == null || npc.getId() != comp.getId())
		{
			npc = findSceneNpc(comp.getId());
		}
		final WorldPoint loc = npc != null && npc.getWorldLocation() != null
			? npc.getWorldLocation()
			: (me != null ? me.getWorldLocation() : null);
		log.debug("onServerNpcLoot rolling '{}' loc={} npcIndex={}", name, loc, npc != null ? npc.getIndex() : -1);
		rollFor(npc != null ? npc.getIndex() : -1, loc, name, new ArrayList<>(items), false);
	}

	/** Claims a loot event for a creature this tick; returns false if it was already claimed (duplicate event). */
	private boolean claimLoot(String monster)
	{
		final int tick = client.getTickCount();
		final Integer prev = handledLootTick.put(monster.toLowerCase(), tick);
		return prev == null || prev != tick;
	}

	/**
	 * An unfinalised roll for this creature means a combat kill is mid-flight (death pre-spin awaiting its loot),
	 * so ServerNpcLoot should defer to it. A finalised, still-lingering roll (e.g. the previous harvest yield)
	 * must NOT match, or rapid harvests get wrongly suppressed.
	 */
	private ActiveRoll findUnfinalisedRollByName(String monster)
	{
		for (ActiveRoll roll : activeRolls)
		{
			if (!roll.isFinalised() && monster.equalsIgnoreCase(roll.getMonster()))
			{
				return roll;
			}
		}
		return null;
	}

	/** Finds the nearest scene NPC matching a composition id, to anchor a harvest reel over the creature. */
	private NPC findSceneNpc(int npcId)
	{
		final Player me = client.getLocalPlayer();
		final WorldPoint mine = me != null ? me.getWorldLocation() : null;
		NPC nearest = null;
		int best = Integer.MAX_VALUE;
		for (NPC npc : client.getNpcs())
		{
			if (npc == null || npc.getId() != npcId)
			{
				continue;
			}
			final WorldPoint loc = npc.getWorldLocation();
			if (loc == null)
			{
				continue;
			}
			if (mine == null)
			{
				return npc;
			}
			final int dist = mine.distanceTo(loc);
			if (dist < best)
			{
				best = dist;
				nearest = npc;
			}
		}
		return nearest;
	}

	/** Entering a raid's reward room: spawn an anticipation reel on the chest, free-spinning its unique table. */
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		final GameObject obj = event.getGameObject();
		final ObjectComposition def = client.getObjectDefinition(obj.getId());
		final String name = def != null ? def.getName() : null;
		// Debug aid: surface reward chest / loot-hole object ids/names so the matching set can be verified/extended.
		if (name != null && !"null".equals(name) && matchesAny(name.toLowerCase(),
			"chest", "sarcophagus", "hole", "burrow", "mokhaiotl", "doom", "remains"))
		{
			log.debug("object spawned id={} name='{}'", obj.getId(), name);
		}
		final RaidChest raid = raidByObject(obj.getId(), name);
		if (raid == null || hasRollFor(raid.page))
		{
			return;
		}
		// Doom's golden hole only spawns when a unique rolled, so spin the uniques pool on a short timeout. The
		// reward chests (ToB/ToA) can take 15s+ to walk to and open, so spin the FULL table persistently until the
		// player's own loot lands (a teammate opening theirs no longer times it out), anchored vertically.
		final boolean doom = "Doom of Mokhaiotl".equals(raid.page);
		startChestAnticipation(raid, obj.getWorldLocation(), false, !doom, !doom);
	}


	/**
	 * Raids reward chests don't fire NpcLootReceived and have no dying NPC, so roll straight off the chest loot.
	 * A purple chest (loot contains a unique) rolls only the uniques; a white chest rolls only the supplies.
	 */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getType() != LootRecordType.EVENT)
		{
			return;
		}
		final RaidChest raid = raidByEventName(event.getName());
		if (raid == null)
		{
			return;
		}
		final Collection<ItemStack> items = event.getItems();
		if (items == null || items.isEmpty())
		{
			return;
		}
		final List<ItemStack> loot = new ArrayList<>(items);
		final Set<Integer> uniqueIds = resolveUniqueIds(raid);
		boolean purple = false;
		for (ItemStack s : loot)
		{
			if (uniqueIds.contains(s.getId()))
			{
				purple = true;
				break;
			}
		}

		if (log.isDebugEnabled())
		{
			final StringBuilder sb = new StringBuilder();
			for (ItemStack s : loot)
			{
				sb.append(itemManager.getItemComposition(s.getId()).getName())
					.append(" x").append(s.getQuantity()).append("; ");
			}
			log.debug("LootReceived '{}' purple={} items=[{}]", raid.page, purple, sb);
		}

		// Reuse the still-spinning anticipation reel so it lands in place; else anchor a fresh one. Either way,
		// re-anchor onto the player's own tile: opening your chest means you're standing at it, so the landed
		// vertical slot settles on YOUR chest rather than the first party chest that happened to spawn the reel.
		final Player me = client.getLocalPlayer();
		final WorldPoint mine = me != null ? me.getWorldLocation() : null;
		ActiveRoll roll = findUnfinalisedRollByName(raid.page);
		if (roll == null)
		{
			// No surviving anticipation reel: anchor a fresh one. CoX renders on the top strip; every other chest
			// is pinned vertical over the player's own chest tile.
			final boolean cox = "Chambers of Xeric".equals(raid.page);
			roll = new ActiveRoll(-1, mine, System.currentTimeMillis(), raid.page);
			roll.setForceHorizontal(cox);
			roll.setForceVertical(!cox);
			activeRolls.add(roll);
		}
		else if (mine != null)
		{
			roll.setLocation(mine);
		}
		finaliseChestRoll(raid, roll, loot, purple);
	}

	/**
	 * CoX early switch: after Olm's head dies the chest loot is announced as "Valuable drop:" game messages ~3s
	 * before the chest loot event. Flag whether any names a unique (purple) and remember the tick, so the spinning
	 * reel can be narrowed one tick later (after the whole burst is in) — see onGameTick.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		final String msg = Text.removeTags(event.getMessage());
		if (msg == null || msg.isEmpty())
		{
			return;
		}
		if (log.isDebugEnabled())
		{
			log.debug("chat[{}]: '{}'", event.getType(), msg);
		}

		if (coxNarrow == 2 || !msg.startsWith("Valuable drop:"))
		{
			return;
		}
		final RaidChest raid = raidByEventName("Chambers of Xeric");
		if (raid == null)
		{
			return;
		}
		final ActiveRoll cox = findUnfinalisedRollByName(raid.page);
		if (cox == null)
		{
			return;
		}
		final String lower = msg.toLowerCase();
		boolean unique = false;
		for (String uname : raid.uniqueNames)
		{
			if (lower.contains(uname))
			{
				unique = true;
				break;
			}
		}
		// Narrow immediately. A unique line upgrades a prior supply-narrow to uniques (purple); supplies only
		// narrow if we're still on the full pool, so a purple is never downgraded to white by a later supply line.
		if (unique)
		{
			switchAnticipationPool(cox, true);
			coxNarrow = 2;
		}
		else if (coxNarrow == 0)
		{
			switchAnticipationPool(cox, false);
			coxNarrow = 1;
		}
	}

	/** Builds a free-spinning reel cycling the raid's unique ("purple") table, anchored over the reward chest. */
	private void startChestAnticipation(RaidChest raid, WorldPoint loc)
	{
		startChestAnticipation(raid, loc, false, false, false);
	}

	/**
	 * CoX: once the "Valuable drop:" lines reveal purple-vs-white, swap the still-spinning full-pool reel to
	 * uniques-only (purple) or supplies-only (white) in place, keeping it spinning. It lands later on the real loot.
	 */
	private void switchAnticipationPool(ActiveRoll roll, boolean purple)
	{
		final RaidChest raid = raidByEventName("Chambers of Xeric");
		if (raid == null || roll.getReels().isEmpty())
		{
			return;
		}
		final List<DropEntry> entries = dropTableService.table(raid.page).getNow(Collections.emptyList());
		final ResolvedTable table = entries.isEmpty() ? tableCache.get(raid.page.toLowerCase()) : resolveTable(raid.page, entries);
		final Set<Integer> uniqueIds = resolveUniqueIds(raid);

		final List<Integer> symbols = new ArrayList<>();
		if (purple)
		{
			symbols.addAll(uniqueIds);
		}
		else if (table != null)
		{
			for (int id : table.getPool())
			{
				if (!uniqueIds.contains(id))
				{
					symbols.add(id);
				}
			}
		}
		symbols.add(NOTHING_ID);
		padTo(symbols, MIN_POOL);
		Collections.shuffle(symbols, random);

		final SlotReel old = roll.getReels().get(0);
		roll.getReels().set(0, new SlotReel(symbols, old.getSpinStartMs(), config.spinSpeed()));
		log.debug("CoX early switch -> {} pool ({} symbols)", purple ? "uniques-only" : "regular-only", symbols.size());
	}

	/**
	 * @param forceHorizontal render the reel on the top-centre strip rather than over a world tile (CoX, where the
	 *                        kill happens far from the chest).
	 * @param persistent      keep spinning until the loot lands instead of timing out after 2x the hold (CoX).
	 * @param fullPool        spin the whole drop table rather than just the uniques (CoX, so a white chest isn't
	 *                        misleadingly cycling purples for the long Olm-kill-to-chest wait).
	 */
	private void startChestAnticipation(RaidChest raid, WorldPoint loc, boolean forceHorizontal, boolean persistent,
		boolean fullPool)
	{
		final List<DropEntry> entries = dropTableService.table(raid.page).getNow(Collections.emptyList());
		final ResolvedTable table = entries.isEmpty() ? tableCache.get(raid.page.toLowerCase()) : resolveTable(raid.page, entries);

		final ActiveRoll roll = new ActiveRoll(-1, loc, System.currentTimeMillis(), raid.page);
		roll.setAnticipation(true);
		roll.setForceHorizontal(forceHorizontal);
		// A reel anchored to the chest/hole tile (everything except CoX's top-strip reel) is pinned vertical so it
		// stays over the object regardless of the global horizontal display toggle.
		roll.setForceVertical(!forceHorizontal);
		roll.setPersistent(persistent);
		if (table != null)
		{
			roll.setPalette(table.getPalette());
		}
		activeRolls.add(roll);

		final List<Integer> symbols = new ArrayList<>(
			fullPool && table != null ? table.getPool() : resolveUniqueIds(raid));
		symbols.add(NOTHING_ID);
		padTo(symbols, MIN_POOL);
		Collections.shuffle(symbols, random);
		roll.getReels().add(new SlotReel(symbols, roll.getSpinStartMs(), config.spinSpeed()));
		log.debug("chest anticipation '{}' (fullPool={})", raid.page, fullPool);
	}

	/** Lands a chest reel: purples roll only the unique pool, whites roll only the supply pool. */
	private void finaliseChestRoll(RaidChest raid, ActiveRoll roll, List<ItemStack> items, boolean purple)
	{
		final List<DropEntry> entries = dropTableService.table(raid.page).getNow(Collections.emptyList());
		final ResolvedTable table = entries.isEmpty() ? tableCache.get(raid.page.toLowerCase()) : resolveTable(raid.page, entries);
		if (table != null)
		{
			roll.setPalette(table.getPalette());
		}
		final Set<Integer> uniqueIds = resolveUniqueIds(raid);

		final Set<Integer> pool = new LinkedHashSet<>();
		final List<ItemStack> hits = new ArrayList<>();
		if (purple)
		{
			pool.addAll(uniqueIds);
			for (ItemStack s : items)
			{
				if (uniqueIds.contains(s.getId()))
				{
					hits.add(s);
				}
			}
		}
		else
		{
			final Set<Integer> alwaysIds = table != null ? table.getAlwaysIds() : Collections.emptySet();
			if (table != null)
			{
				for (int id : table.getPool())
				{
					if (!uniqueIds.contains(id))
					{
						pool.add(id);
					}
				}
			}
			for (ItemStack s : items)
			{
				if (uniqueIds.contains(s.getId()) || alwaysIds.contains(s.getId()))
				{
					continue;
				}
				if (itemManager.getItemPrice(s.getId()) < config.minItemValue())
				{
					continue;
				}
				hits.add(s);
				pool.add(s.getId());
			}
		}
		hits.sort((a, b) -> Long.compare(value(b), value(a)));

		int rolls = Math.min(Math.max(hits.size(), 1), config.maxReels());
		pool.add(NOTHING_ID);
		final List<Integer> basePool = new ArrayList<>(pool);
		final long now = System.currentTimeMillis();
		final int settle = config.settleMs();
		final List<SlotReel> reels = new ArrayList<>();

		final int hitCount = Math.min(hits.size(), rolls);
		for (int i = 0; i < hitCount; i++)
		{
			final ItemStack s = hits.get(i);
			reels.add(buildReel(basePool, roll.getSpinStartMs(), now, s.getId(), s.getQuantity(), settle));
		}
		for (int i = hitCount; i < rolls; i++)
		{
			reels.add(buildReel(basePool, roll.getSpinStartMs(), now, NOTHING_ID, 1, settle));
		}
		roll.setReels(reels);
		log.debug("finalise chest '{}' purple={} rolls={} hits={}", raid.page, purple, rolls, hits.size());
	}

	private static boolean matchesAny(String haystack, String... needles)
	{
		for (String n : needles)
		{
			if (haystack.contains(n))
			{
				return true;
			}
		}
		return false;
	}

	/** True for raid room bosses whose loot is delivered by the raid chest, so they must not roll themselves. */
	private static boolean isRaidChestBoss(String name)
	{
		return name != null && RAID_CHEST_BOSSES.contains(name.toLowerCase());
	}

	private static RaidChest raidByObject(int id, String name)
	{
		final String lower = name == null ? null : name.toLowerCase();
		for (RaidChest raid : RAID_CHESTS)
		{
			if (raid.objectIds.contains(id) || (lower != null && raid.objectNames.contains(lower)))
			{
				return raid;
			}
		}
		return null;
	}

	private static RaidChest raidByEventName(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (RaidChest raid : RAID_CHESTS)
		{
			if (name.startsWith(raid.eventPrefix))
			{
				return raid;
			}
		}
		return null;
	}

	private Set<Integer> resolveUniqueIds(RaidChest raid)
	{
		return raidUniqueIds.computeIfAbsent(raid.page, k ->
		{
			final Set<Integer> ids = new HashSet<>();
			for (String name : raid.uniqueNames)
			{
				final int id = resolveItemId(name);
				if (id > 0)
				{
					ids.add(id);
				}
			}
			return ids;
		});
	}

	/** True if any roll (spinning or landed) is already showing for this chest page. */
	private boolean hasRollFor(String page)
	{
		for (ActiveRoll roll : activeRolls)
		{
			if (page.equalsIgnoreCase(roll.getMonster()))
			{
				return true;
			}
		}
		return false;
	}

	/** Records the drop and spins a roll for a loot source (NPC kill or raids chest), reusing any in-flight roll. */
	private void rollFor(int index, WorldPoint location, String source, List<ItemStack> items, boolean forceHorizontal)
	{
		final Set<Integer> seen = seenDrops.computeIfAbsent(source.toLowerCase(), k -> ConcurrentHashMap.newKeySet());
		for (ItemStack s : items)
		{
			seen.add(s.getId());
			globalSeen.add(s.getId());
		}

		ActiveRoll roll = index >= 0 ? findRoll(index) : null;
		if (roll == null)
		{
			// Land the creature's spinning death/harvest pre-spin if one exists (it may be anchored under a
			// different index, e.g. a harvest creature detected as "dying"), so it settles on the real loot.
			roll = findUnfinalisedRollByName(source);
		}
		if (roll == null)
		{
			roll = new ActiveRoll(index, location, System.currentTimeMillis(), source);
			roll.setForceHorizontal(forceHorizontal);
			activeRolls.add(roll);
		}
		final ActiveRoll target = roll;

		final CompletableFuture<List<DropEntry>> future = dropTableService.table(source);
		final List<DropEntry> cached = future.getNow(null);
		if (cached != null)
		{
			finaliseRoll(target, items, cached);
		}
		else
		{
			future.thenAccept(entries ->
				clientThread.invoke(() -> finaliseRoll(target, items, entries)));
		}
	}

	private void finaliseRoll(ActiveRoll roll, List<ItemStack> items, List<DropEntry> entries)
	{
		final ResolvedTable table = !entries.isEmpty()
			? resolveTable(roll.getMonster(), entries)
			: tableCache.get(roll.getMonster().toLowerCase());
		final boolean haveWiki = table != null;

		final Set<Integer> alwaysIds = haveWiki ? table.getAlwaysIds() : Collections.emptySet();
		final Set<Integer> pool = new LinkedHashSet<>();
		if (haveWiki)
		{
			pool.addAll(table.getPool());
			roll.setPalette(table.getPalette());
		}
		else
		{
			pool.addAll(seenDrops.getOrDefault(roll.getMonster().toLowerCase(), Collections.emptySet()));
		}

		final List<ItemStack> hits = new ArrayList<>();
		for (ItemStack s : items)
		{
			if (alwaysIds.contains(s.getId()))
			{
				continue;
			}
			if (itemManager.getItemPrice(s.getId()) < config.minItemValue())
			{
				continue;
			}
			hits.add(s);
			pool.add(s.getId());
		}
		hits.sort((a, b) -> Long.compare(value(b), value(a)));

		final int mainRolls = haveWiki ? table.getMainRolls() : hits.size();
		int rolls = Math.max(mainRolls, hits.size());
		rolls = Math.min(rolls, config.maxReels());

		if (rolls <= 0)
		{
			activeRolls.remove(roll);
			return;
		}

		pool.add(NOTHING_ID);
		final List<Integer> basePool = new ArrayList<>(pool);
		final long now = System.currentTimeMillis();
		final int settle = config.settleMs();
		final List<SlotReel> reels = new ArrayList<>();

		final int hitCount = Math.min(hits.size(), rolls);
		for (int i = 0; i < hitCount; i++)
		{
			final ItemStack s = hits.get(i);
			reels.add(buildReel(basePool, roll.getSpinStartMs(), now, s.getId(), s.getQuantity(), settle));
		}
		for (int i = hitCount; i < rolls; i++)
		{
			reels.add(buildReel(basePool, roll.getSpinStartMs(), now, NOTHING_ID, 1, settle));
		}

		roll.setReels(reels);
		log.debug("finalise '{}' rolls={} hits={} (loot spawned)", roll.getMonster(), rolls, hits.size());
	}

	private SlotReel buildReel(List<Integer> basePool, long spinStartMs, long now, int targetId, int quantity, int settle)
	{
		final List<Integer> symbols = new ArrayList<>(basePool);
		if (!symbols.contains(targetId))
		{
			symbols.add(targetId);
		}
		padTo(symbols, MIN_POOL);
		Collections.shuffle(symbols, random);

		final SlotReel reel = new SlotReel(symbols, spinStartMs, config.spinSpeed());
		reel.land(now, targetId, quantity, settle, MIN_LAND_LOOPS);
		return reel;
	}

	/** Resolve a monster's wiki entries to item ids, rarity colours, guaranteed-drop ids and roll count; cached per monster. */
	private ResolvedTable resolveTable(String monster, List<DropEntry> entries)
	{
		final ResolvedTable existing = tableCache.get(monster.toLowerCase());
		if (existing != null || entries.isEmpty())
		{
			return existing;
		}

		final boolean hideRdt = config.hideRareDropTable();
		final Set<String> nonAlwaysNames = new LinkedHashSet<>();
		final Set<String> anyAlwaysNames = new HashSet<>();
		final Map<Integer, Integer> rollCounts = new HashMap<>();
		final Map<String, Double> nameChance = new HashMap<>();
		for (DropEntry e : entries)
		{
			if (e.isAlways())
			{
				anyAlwaysNames.add(e.getName());
			}
			else if (!(hideRdt && e.isRdt()))
			{
				// Skip shared rare/gem/mega drop-table rows; a name shared with a real drop keeps that row's
				// chance/colour, and an item with only rare-table rows drops out of the reel pool entirely.
				nonAlwaysNames.add(e.getName());
				rollCounts.merge(e.getRolls(), 1, Integer::sum);
				nameChance.merge(e.getName(), e.getChance(), LoulettePlugin::rarer);
			}
		}
		anyAlwaysNames.removeAll(nonAlwaysNames);

		final Set<Integer> alwaysIds = new HashSet<>();
		for (String name : anyAlwaysNames)
		{
			final int id = resolveItemId(name);
			if (id > 0)
			{
				alwaysIds.add(id);
			}
		}

		final List<Integer> pool = new ArrayList<>();
		final Map<Integer, Double> idChance = new HashMap<>();
		for (String name : nonAlwaysNames)
		{
			final int id = resolveItemId(name);
			if (id > 0)
			{
				if (!idChance.containsKey(id))
				{
					pool.add(id);
				}
				idChance.merge(id, nameChance.getOrDefault(name, -1.0), LoulettePlugin::rarer);
			}
		}
		final Map<Integer, Color> palette = new HashMap<>();
		for (Map.Entry<Integer, Double> e : idChance.entrySet())
		{
			palette.put(e.getKey(), Rarity.colour(e.getValue(), config));
		}

		final boolean alwaysOnly = nonAlwaysNames.isEmpty();
		final int mainRolls = alwaysOnly ? 0 : mode(rollCounts);
		final ResolvedTable table = new ResolvedTable(pool, palette, alwaysIds, mainRolls, alwaysOnly);
		tableCache.put(monster.toLowerCase(), table);
		return table;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			activeRolls.clear();
			engagedUntil.clear();
			handledDeaths.clear();
		}
	}

	private boolean isEngaged(int index, long now)
	{
		final Long until = engagedUntil.get(index);
		return until != null && until >= now;
	}

	private ActiveRoll findRoll(int npcIndex)
	{
		for (ActiveRoll roll : activeRolls)
		{
			if (roll.getNpcIndex() == npcIndex && !roll.isFinalised())
			{
				return roll;
			}
		}
		return null;
	}

	/** Combine two drop chances, preferring a known fraction over varies/unknown, then the rarer (smaller). */
	private static double rarer(double a, double b)
	{
		if (!(a > 0)) // unknown or varies (NaN)
		{
			return b;
		}
		if (!(b > 0))
		{
			return a;
		}
		return Math.min(a, b);
	}

	private static int mode(Map<Integer, Integer> counts)
	{
		int best = 1;
		int bestCount = 0;
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() < best))
			{
				best = e.getKey();
				bestCount = e.getValue();
			}
		}
		return Math.max(1, best);
	}

	private void padTo(List<Integer> symbols, int min)
	{
		if (symbols.isEmpty())
		{
			return;
		}
		int i = 0;
		while (symbols.size() < min)
		{
			symbols.add(symbols.get(i % symbols.size()));
			i++;
		}
	}

	private long value(ItemStack stack)
	{
		return (long) itemManager.getItemPrice(stack.getId()) * Math.max(1, stack.getQuantity());
	}

	private int resolveItemId(String name)
	{
		if (name == null || name.isEmpty())
		{
			return -1;
		}
		if (name.equalsIgnoreCase("Coins"))
		{
			return COINS_ITEM_ID;
		}
		// Prefer the bundled id: it covers untradeables (pets, clue scrolls, boss uniques) that
		// ItemManager.search — which only knows GE-tradeable items — would otherwise drop from the reel.
		final int bundled = dropTableService.itemId(name);
		if (bundled > 0)
		{
			return bundled;
		}
		final List<ItemPrice> results = itemManager.search(name);
		if (results.isEmpty())
		{
			return -1;
		}
		for (ItemPrice p : results)
		{
			if (name.equalsIgnoreCase(p.getName()))
			{
				return p.getId();
			}
		}
		return results.get(0).getId();
	}

	@Provides
	LouletteConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LouletteConfig.class);
	}
}
