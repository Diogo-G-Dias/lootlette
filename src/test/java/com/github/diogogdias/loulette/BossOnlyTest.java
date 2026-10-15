package com.github.diogogdias.loulette;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BossOnlyTest
{
	@Mock private Client client;
	@Mock private ItemManager itemManager;
	@Mock private DropTableService dropTableService;
	@Mock private ClientThread clientThread;
	@Spy private TestConfig config = new TestConfig();
	@InjectMocks private LoulettePlugin plugin;

	private NPC npc(String name)
	{
		NPC npc = mock(NPC.class);
		when(npc.getName()).thenReturn(name);
		return npc;
	}

	private void emptyTable(String name)
	{
		when(dropTableService.table(name)).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
	}

	private NpcLootReceived groundLoot(String name)
	{
		return new NpcLootReceived(npc(name), Collections.singletonList(new ItemStack(995, 100)));
	}

	private ServerNpcLoot serverLoot(String name)
	{
		NPCComposition comp = mock(NPCComposition.class);
		when(comp.getName()).thenReturn(name);
		return new ServerNpcLoot(comp, Collections.singletonList(new ItemStack(995, 100)));
	}

	private ConfigChanged changed(String key)
	{
		ConfigChanged event = new ConfigChanged();
		event.setGroup(LoulettePlugin.CONFIG_GROUP);
		event.setKey(key);
		return event;
	}

	@Test
	public void settingIsOffByDefault()
	{
		assertFalse(new LouletteConfig() {}.bossOnly());
		assertEquals("", new LouletteConfig() {}.whitelistedNpcs());
	}

	private void whitelist(String names)
	{
		config.whitelisted = names;
		plugin.onConfigChanged(changed("whitelistedNpcs"));
	}

	@Test
	public void whitelistAllowsGwdMinionsThroughBothLootPaths()
	{
		whitelist("Sergeant Strongstack, Sergeant Steelwill");
		emptyTable("Sergeant Strongstack");
		emptyTable("Sergeant Steelwill");
		plugin.onNpcLootReceived(groundLoot("Sergeant Strongstack"));
		plugin.onServerNpcLoot(serverLoot("Sergeant Steelwill"));
		assertEquals(2, plugin.getActiveRolls().size());
		assertTrue(plugin.getActiveRolls().stream().allMatch(ActiveRoll::isFinalised));
	}

	@Test
	public void whitelistIsAdditiveAndDoesNotReplaceTheBossList()
	{
		whitelist("Sergeant Strongstack");
		emptyTable("Zulrah");
		plugin.onNpcLootReceived(groundLoot("Zulrah"));
		assertEquals("Zulrah", plugin.getActiveRolls().get(0).getMonster());
	}

	@Test
	public void whitelistTrimsNamesIgnoresCaseAndDoesNotMatchSubstrings()
	{
		whitelist(" ,  sErGeAnT StRoNgStAcK , , ");
		emptyTable("Sergeant Strongstack");
		plugin.onNpcLootReceived(groundLoot("Sergeant Strongstack"));
		plugin.onNpcLootReceived(groundLoot("Sergeant Steelwill"));
		assertEquals(1, plugin.getActiveRolls().size());
		assertEquals("Sergeant Strongstack", plugin.getActiveRolls().get(0).getMonster());
	}

	@Test
	public void ignoredNameWinsOverWhitelistWithEitherBossOnlySetting()
	{
		whitelist("sergeant strongstack");
		config.ignored = " SERGEANT STRONGSTACK ";
		plugin.onConfigChanged(changed("ignoredNpcs"));
		for (boolean onlyBosses : new boolean[]{true, false})
		{
			config.onlyBosses = onlyBosses;
			plugin.onNpcLootReceived(groundLoot("Sergeant Strongstack"));
			plugin.onServerNpcLoot(serverLoot("Sergeant Strongstack"));
		}
		assertTrue(plugin.getActiveRolls().isEmpty());
		verifyNoInteractions(dropTableService, itemManager);
	}

	@Test
	public void whitelistDoesNotRestrictOrdinaryLootWhenBossOnlyIsOff()
	{
		config.onlyBosses = false;
		whitelist("Sergeant Strongstack");
		emptyTable("Goblin");
		plugin.onNpcLootReceived(groundLoot("Goblin"));
		assertEquals("Goblin", plugin.getActiveRolls().get(0).getMonster());
	}

	@Test
	public void removingAWhitelistEntryClearsItsExistingReelButKeepsBossesAndChests()
	{
		whitelist("Sergeant Strongstack");
		for (String name : new String[]{"Sergeant Strongstack", "Zulrah", "Ancient chest"})
		{
			plugin.getActiveRolls().add(new ActiveRoll(-1, null, System.currentTimeMillis(), name));
		}
		plugin.onConfigChanged(changed("bossOnly"));
		assertEquals(3, plugin.getActiveRolls().size());
		whitelist("");
		assertEquals(2, plugin.getActiveRolls().size());
		assertFalse(plugin.getActiveRolls().stream().anyMatch(roll -> "Sergeant Strongstack".equals(roll.getMonster())));
	}

	@Test
	public void whitelistedMinionDeathStartsAnAnticipationReel()
	{
		whitelist("Sergeant Strongstack");
		NPC minion = npc("Sergeant Strongstack");
		when(minion.isDead()).thenReturn(true);
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getInteracting()).thenReturn(minion);
		when(client.getNpcs()).thenReturn(Collections.singletonList(minion));
		when(dropTableService.table("Sergeant Strongstack")).thenReturn(CompletableFuture.completedFuture(
			Collections.singletonList(new DropEntry("Coins", false, 1, 0.5, false))));
		plugin.onGameTick(new GameTick());
		assertEquals(1, plugin.getActiveRolls().size());
		assertFalse(plugin.getActiveRolls().get(0).isFinalised());
	}

	@Test
	public void removingWhitelistEntryCancelsItsPendingTableResult()
	{
		whitelist("Sergeant Strongstack");
		CompletableFuture<java.util.List<DropEntry>> pending = new CompletableFuture<>();
		when(dropTableService.table("Sergeant Strongstack")).thenReturn(pending);
		plugin.onNpcLootReceived(groundLoot("Sergeant Strongstack"));
		assertEquals(1, plugin.getActiveRolls().size());
		whitelist("");
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		pending.complete(Collections.emptyList());
		assertTrue(plugin.getActiveRolls().isEmpty());
		verifyNoInteractions(itemManager);
	}

	@Test
	public void blocksOrdinaryGroundLootAndBossMinions()
	{
		plugin.onNpcLootReceived(groundLoot("Goblin"));
		plugin.onNpcLootReceived(groundLoot("Sergeant Strongstack"));
		assertTrue(plugin.getActiveRolls().isEmpty());
		verifyNoInteractions(dropTableService, itemManager);
	}

	@Test
	public void blocksServerHarvestsAndMinions()
	{
		plugin.onServerNpcLoot(serverLoot("Giant kraken"));
		plugin.onServerNpcLoot(serverLoot("Sergeant Strongstack"));
		assertTrue(plugin.getActiveRolls().isEmpty());
		verifyNoInteractions(dropTableService, itemManager);
	}

	@Test
	public void allowsBossGroundLoot()
	{
		emptyTable("Zulrah");
		plugin.onNpcLootReceived(groundLoot("Zulrah"));
		assertEquals(1, plugin.getActiveRolls().size());
		assertTrue(plugin.getActiveRolls().get(0).isFinalised());
	}

	@Test
	public void allowsBossServerLoot()
	{
		emptyTable("The Nightmare");
		plugin.onServerNpcLoot(serverLoot("The Nightmare"));
		assertEquals(1, plugin.getActiveRolls().size());
		assertTrue(plugin.getActiveRolls().get(0).isFinalised());
	}

	@Test
	public void disabledFilterKeepsOrdinaryLoot()
	{
		config.onlyBosses = false;
		emptyTable("Goblin");
		plugin.onNpcLootReceived(groundLoot("Goblin"));
		assertEquals("Goblin", plugin.getActiveRolls().get(0).getMonster());
	}

	@Test
	public void ignoredBossStillDoesNotRoll()
	{
		config.ignored = "Zulrah";
		plugin.onConfigChanged(changed("ignoredNpcs"));
		plugin.onNpcLootReceived(groundLoot("Zulrah"));
		plugin.onServerNpcLoot(serverLoot("Zulrah"));
		assertTrue(plugin.getActiveRolls().isEmpty());
		verifyNoInteractions(dropTableService);
	}

	@Test
	public void nonBossDeathNeverStartsAnAnticipationReel()
	{
		NPC goblin = npc("Goblin");
		when(goblin.isDead()).thenReturn(true);
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getInteracting()).thenReturn(goblin);
		when(client.getNpcs()).thenReturn(Collections.singletonList(goblin));
		plugin.onGameTick(new GameTick());
		assertTrue(plugin.getActiveRolls().isEmpty());
		verifyNoInteractions(dropTableService);
	}

	@Test
	public void bossDeathStillStartsAnAnticipationReel()
	{
		NPC zulrah = npc("Zulrah");
		when(zulrah.isDead()).thenReturn(true);
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getInteracting()).thenReturn(zulrah);
		when(client.getNpcs()).thenReturn(Collections.singletonList(zulrah));
		when(dropTableService.table("Zulrah")).thenReturn(CompletableFuture.completedFuture(
			Collections.singletonList(new DropEntry("Coins", false, 1, 0.5, false))));
		plugin.onGameTick(new GameTick());
		assertEquals(1, plugin.getActiveRolls().size());
		assertFalse(plugin.getActiveRolls().get(0).isFinalised());
	}

	@Test
	public void enablingFilterRemovesOrdinaryReelsButKeepsBossAndRewardChestReels()
	{
		for (String name : new String[]{"Goblin", "Zulrah", "Ancient chest", "Monumental chest",
			"Chest (Tombs of Amascut)", "Doom of Mokhaiotl"})
		{
			plugin.getActiveRolls().add(new ActiveRoll(-1, null, System.currentTimeMillis(), name));
		}
		plugin.onConfigChanged(changed("bossOnly"));
		assertEquals(5, plugin.getActiveRolls().size());
		assertFalse(plugin.getActiveRolls().stream().anyMatch(roll -> "Goblin".equals(roll.getMonster())));
	}

	@Test
	public void keepsRaidRewardLootWithBossOnlyEnabled()
	{
		emptyTable("Ancient chest");
		plugin.onLootReceived(new LootReceived("Chambers of Xeric", -1, LootRecordType.EVENT,
			Collections.singletonList(new ItemStack(995, 100)), 0, null));
		assertEquals(1, plugin.getActiveRolls().size());
		assertEquals("Ancient chest", plugin.getActiveRolls().get(0).getMonster());
		assertTrue(plugin.getActiveRolls().get(0).isFinalised());
	}

	@Test
	public void ignoresLateTableLookupForAFilteredReel()
	{
		config.onlyBosses = false;
		CompletableFuture<java.util.List<DropEntry>> pending = new CompletableFuture<>();
		when(dropTableService.table("Goblin")).thenReturn(pending);
		plugin.onNpcLootReceived(groundLoot("Goblin"));
		assertEquals(1, plugin.getActiveRolls().size());
		config.onlyBosses = true;
		plugin.onConfigChanged(changed("bossOnly"));
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		pending.complete(Collections.emptyList());
		assertTrue(plugin.getActiveRolls().isEmpty());
		verifyNoInteractions(itemManager);
	}

	static class TestConfig implements LouletteConfig
	{
		boolean onlyBosses = true;
		String ignored = "";
		String whitelisted = "";

		@Override
		public boolean bossOnly()
		{
			return onlyBosses;
		}

		@Override
		public String ignoredNpcs()
		{
			return ignored;
		}

		@Override
		public String whitelistedNpcs()
		{
			return whitelisted;
		}
	}
}
