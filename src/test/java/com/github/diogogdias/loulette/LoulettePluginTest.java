package com.github.diogogdias.loulette;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LoulettePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LoulettePlugin.class);
		RuneLite.main(args);
	}
}
