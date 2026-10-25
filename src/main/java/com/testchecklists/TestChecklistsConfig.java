package com.testchecklists;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(TestChecklistsConfig.GROUP)
public interface TestChecklistsConfig extends Config
{
	String GROUP = "testchecklists";

	@ConfigItem(
		keyName = "feedUrl",
		name = "Checklist feed URL",
		description = "Where your server publishes its checklists.json (see the README for the format)",
		position = 0
	)
	default String feedUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "reportCommand",
		name = "Report command",
		description = "Typed into the chatbox as: <command> <checklist> <item> <pass|fail> [note]",
		position = 1
	)
	default String reportCommand()
	{
		return "::testreport";
	}

	@ConfigItem(
		keyName = "enableMessage",
		name = "Enable message",
		description = "A game message the server sends to testers at login. When set, the panel appears only after "
			+ "this exact message arrives; when empty, it is always available.",
		position = 2
	)
	default String enableMessage()
	{
		return "";
	}

	@ConfigItem(
		keyName = "refusedMessage",
		name = "Refused message",
		description = "A game message the server sends when this account may not report (shown in the panel)",
		position = 3
	)
	default String refusedMessage()
	{
		return "Only testers can send test reports.";
	}
}
