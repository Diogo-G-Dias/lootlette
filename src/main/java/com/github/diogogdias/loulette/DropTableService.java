package com.github.diogogdias.loulette;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Serves a monster's drop table. The primary source is a bundled snapshot of the entire OSRS Wiki
 * {@code dropsline} bucket (see {@code scripts/scrape-drops.mjs}), loaded once at startup — no network
 * request, always available. Monsters missing from the snapshot fall back to a live wiki fetch, but only
 * when the user opts in via {@link LouletteConfig#wikiDropTables()}.
 */
@Slf4j
@Singleton
class DropTableService
{
	private static final HttpUrl API = HttpUrl.get("https://oldschool.runescape.wiki/api.php");
	private static final int ROW_LIMIT = 300;
	private static final String SNAPSHOT = "drops.json.gz";
	private static final CompletableFuture<List<DropEntry>> EMPTY =
		CompletableFuture.completedFuture(Collections.emptyList());

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final LouletteConfig config;

	/** Bundled snapshot: lower-cased page name -> drop lines. Immutable after load. */
	private final Map<String, List<DropEntry>> bundled;

	private final Map<String, CompletableFuture<List<DropEntry>>> cache = new ConcurrentHashMap<>();

	@Inject
	DropTableService(OkHttpClient okHttpClient, Gson gson, LouletteConfig config)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.config = config;
		this.bundled = loadSnapshot(gson);
	}

	CompletableFuture<List<DropEntry>> table(String monster)
	{
		final String key = monster.toLowerCase();
		final List<DropEntry> snapshot = bundled.get(key);
		if (snapshot != null)
		{
			return CompletableFuture.completedFuture(snapshot);
		}
		if (config.wikiDropTables())
		{
			return cache.computeIfAbsent(key, k -> request(monster));
		}
		return EMPTY;
	}

	/** Reads the gzipped snapshot bundled in resources into an immutable name -> drop-lines map. */
	private static Map<String, List<DropEntry>> loadSnapshot(Gson gson)
	{
		try (InputStream raw = DropTableService.class.getResourceAsStream(SNAPSHOT))
		{
			if (raw == null)
			{
				log.warn("Drop-table snapshot '{}' missing; relying on the live wiki fallback only", SNAPSHOT);
				return Collections.emptyMap();
			}
			try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))
			{
				final JsonObject root = gson.fromJson(reader, JsonObject.class);
				final JsonObject monsters = root.getAsJsonObject("monsters");
				final Map<String, List<DropEntry>> out = new HashMap<>(monsters.size() * 2);
				for (Map.Entry<String, JsonElement> e : monsters.entrySet())
				{
					out.put(e.getKey(), Collections.unmodifiableList(parseLines(e.getValue().getAsJsonArray())));
				}
				log.debug("Loaded drop-table snapshot: {} monsters (updated {})",
					out.size(), root.has("lastUpdated") ? root.get("lastUpdated").getAsString() : "?");
				return Collections.unmodifiableMap(out);
			}
		}
		catch (Exception e)
		{
			log.warn("Failed to load drop-table snapshot '{}': {}", SNAPSHOT, e.getMessage());
			return Collections.emptyMap();
		}
	}

	/** Each line is {@code [name, rarity]} or {@code [name, rarity, rolls]}; rolls defaults to 1. */
	private static List<DropEntry> parseLines(JsonArray lines)
	{
		final List<DropEntry> entries = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++)
		{
			final JsonArray line = lines.get(i).getAsJsonArray();
			final String name = line.get(0).getAsString();
			final String rarity = line.size() > 1 && !line.get(1).isJsonNull() ? line.get(1).getAsString() : "";
			final int rolls = line.size() > 2 ? Math.max(1, line.get(2).getAsInt()) : 1;
			entries.add(new DropEntry(name, "Always".equalsIgnoreCase(rarity), rolls, parseChance(rarity)));
		}
		return entries;
	}

	private CompletableFuture<List<DropEntry>> request(String monster)
	{
		final CompletableFuture<List<DropEntry>> future = new CompletableFuture<>();

		final String query = "bucket('dropsline').select('item_name','drop_json')"
			+ ".where('page_name','" + monster.replace("'", "\\'") + "')"
			+ ".limit(" + ROW_LIMIT + ").run()";

		final HttpUrl url = API.newBuilder()
			.addQueryParameter("action", "bucket")
			.addQueryParameter("format", "json")
			.addQueryParameter("query", query)
			.build();

		final Request req = new Request.Builder()
			.url(url)
			.header("User-Agent", "loulette RuneLite plugin")
			.build();

		okHttpClient.newCall(req).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Drop table fetch failed for '{}': {}", monster, e.getMessage());
				future.complete(new ArrayList<>());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					future.complete(parse(r, monster));
				}
				catch (Exception e)
				{
					log.debug("Drop table parse failed for '{}': {}", monster, e.getMessage());
					future.complete(new ArrayList<>());
				}
			}
		});

		return future;
	}

	private List<DropEntry> parse(Response response, String monster) throws IOException
	{
		final List<DropEntry> entries = new ArrayList<>();
		if (!response.isSuccessful() || response.body() == null)
		{
			return entries;
		}

		final JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
		if (root == null || !root.has("bucket") || !root.get("bucket").isJsonArray())
		{
			return entries;
		}

		final JsonArray rows = root.getAsJsonArray("bucket");
		for (int i = 0; i < rows.size(); i++)
		{
			final JsonObject row = rows.get(i).getAsJsonObject();
			if (!row.has("item_name") || row.get("item_name").isJsonNull())
			{
				continue;
			}
			final String name = row.get("item_name").getAsString().trim();
			if (name.isEmpty() || "Nothing".equalsIgnoreCase(name))
			{
				continue;
			}

			boolean always = false;
			int rolls = 1;
			double chance = -1;
			if (row.has("drop_json") && !row.get("drop_json").isJsonNull())
			{
				try
				{
					final JsonObject dj = gson.fromJson(row.get("drop_json").getAsString(), JsonObject.class);
					final String rarity = optString(dj, "Rarity");
					always = "Always".equalsIgnoreCase(rarity);
					rolls = Math.max(1, optInt(dj, "Rolls", 1));
					chance = parseChance(rarity);
				}
				catch (Exception ignored)
				{
					// keep defaults
				}
			}
			entries.add(new DropEntry(name, always, rolls, chance));
		}

		log.debug("Drop table for '{}': {} lines", monster, entries.size());
		return entries;
	}

	private static String optString(JsonObject o, String key)
	{
		final JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	/** Parses a wiki rarity string ("1/128", "3/128", "~1/5,000", "Always") into a probability; -1 if unknown. */
	private static double parseChance(String rarity)
	{
		if (rarity == null)
		{
			return -1;
		}
		final String r = rarity.replace(",", "").replace("~", "").trim().toLowerCase();
		if (r.equals("varies") || r.equals("random"))
		{
			return Double.NaN;
		}
		if (r.isEmpty() || r.equals("unknown"))
		{
			return -1;
		}
		if (r.equals("always"))
		{
			return 1.0;
		}
		final int slash = r.indexOf('/');
		if (slash > 0)
		{
			try
			{
				final double num = Double.parseDouble(r.substring(0, slash).trim());
				final double den = Double.parseDouble(r.substring(slash + 1).trim());
				return den > 0 ? num / den : -1;
			}
			catch (NumberFormatException ex)
			{
				return -1;
			}
		}
		switch (r)
		{
			case "common":
				return 1 / 12.0;
			case "uncommon":
				return 1 / 60.0;
			case "rare":
				return 1 / 400.0;
			case "very rare":
				return 1 / 2000.0;
			default:
				return -1;
		}
	}

	private static int optInt(JsonObject o, String key, int def)
	{
		final JsonElement e = o.get(key);
		if (e == null || e.isJsonNull())
		{
			return def;
		}
		try
		{
			return e.getAsInt();
		}
		catch (NumberFormatException ex)
		{
			return def;
		}
	}
}
