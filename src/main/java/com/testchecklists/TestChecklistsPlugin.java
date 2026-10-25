package com.testchecklists;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Test Checklists: a server's test checklists in a side panel, with Pass/Fail reports sent back to
 * the game server.
 *
 * <p>The checklists come from a JSON feed (the {@code Checklist feed URL} setting). Pass and Fail
 * are sent as {@code <report command> <checklist> <item> <pass|fail> [note]}, typed into the
 * chatbox the way a player would, so the report carries the logged-in account and the server needs
 * nothing but a chat command. The server answers {@code Test report recorded: <checklist> #<item>
 * <pass|fail>.} and the plugin marks the item. The README describes the feed and the protocol.
 */
@Slf4j
@PluginDescriptor(
	name = "Test Checklists",
	description = "Your server's test checklists in a side panel, with Pass/Fail reports sent to the server",
	tags = {"testing", "checklist", "qa", "private server"}
)
public class TestChecklistsPlugin extends Plugin
{
	private static final Pattern RECORDED = Pattern.compile("Test report recorded: ([A-Za-z0-9_-]+) #(\\d+) (pass|fail)\\.");
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]+");
	/** VarClientStr.CHATBOX_TYPED_TEXT: what the player has typed but not sent. */
	private static final int CHATBOX_TYPED_TEXT = 335;
	private static final int MAX_NOTE = 200;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private TestChecklistsConfig config;

	@Inject
	private OkHttpClient http;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	private TestsPanel panel;
	private NavigationButton button;

	@Provides
	TestChecklistsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TestChecklistsConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = new TestsPanel(this);
		if (!gated() && client.getGameState() == GameState.LOGGED_IN)
		{
			SwingUtilities.invokeLater(this::show);
		}
	}

	@Override
	protected void shutDown()
	{
		SwingUtilities.invokeLater(this::hide);
		panel = null;
	}

	/** True when the panel waits for the server's enable message. */
	private boolean gated()
	{
		return !config.enableMessage().trim().isEmpty();
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String text = event.getMessage();
		if (gated() && config.enableMessage().trim().equals(text))
		{
			SwingUtilities.invokeLater(this::show);
			return;
		}
		Matcher m = RECORDED.matcher(text);
		if (m.matches())
		{
			String checklist = m.group(1);
			int item = Integer.parseInt(m.group(2));
			String result = m.group(3);
			configManager.setRSProfileConfiguration(TestChecklistsConfig.GROUP, checklist + "." + item, result);
			SwingUtilities.invokeLater(() -> panel.recorded(checklist, item, result));
		}
		else if (text.equals(config.refusedMessage()))
		{
			SwingUtilities.invokeLater(() -> panel.status("The server says this account can't send test reports."));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			SwingUtilities.invokeLater(this::hide);
		}
		else if (event.getGameState() == GameState.LOGGED_IN && !gated())
		{
			SwingUtilities.invokeLater(this::show);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (TestChecklistsConfig.GROUP.equals(event.getGroup()) && "feedUrl".equals(event.getKey()) && button != null)
		{
			SwingUtilities.invokeLater(this::refresh);
		}
	}

	private void show()
	{
		if (button != null || panel == null)
		{
			return;
		}
		button = NavigationButton.builder()
			.tooltip("Test Checklists")
			.icon(icon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(button);
		refresh();
	}

	private void hide()
	{
		if (button != null)
		{
			clientToolbar.removeNavigation(button);
			button = null;
		}
	}

	/** Fetches the feed again. */
	void refresh()
	{
		HttpUrl url = HttpUrl.parse(config.feedUrl().trim());
		if (url == null)
		{
			panel.setFeed(null);
			panel.status("Set the checklist feed URL in this plugin's settings.");
			return;
		}
		panel.status("Loading checklists...");
		Request request = new Request.Builder().url(url).build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Test Checklists feed failed", e);
				SwingUtilities.invokeLater(() -> panel.status("Couldn't load the checklists: " + e.getMessage()));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						SwingUtilities.invokeLater(() -> panel.status("Couldn't load the checklists (HTTP " + r.code() + ")."));
						return;
					}
					Feed feed = gson.fromJson(r.body().string(), Feed.class);
					SwingUtilities.invokeLater(() -> panel.setFeed(feed));
				}
				catch (RuntimeException e)
				{
					log.warn("Test Checklists feed is not valid JSON", e);
					SwingUtilities.invokeLater(() -> panel.status("The checklist feed isn't in the expected format."));
				}
			}
		});
	}

	/** The last result this account recorded for an item, or null. */
	String recordedResult(String checklist, int item)
	{
		return configManager.getRSProfileConfiguration(TestChecklistsConfig.GROUP, checklist + "." + item);
	}

	/**
	 * Sends the report command by typing it into the chatbox. Refuses if the player has something
	 * typed already, so their message isn't mangled.
	 */
	void report(String checklist, int item, String result, String note)
	{
		String command = config.reportCommand().trim();
		if (command.isEmpty() || !SAFE_ID.matcher(checklist).matches())
		{
			panel.status("Can't send this report: check the report command setting and the feed's checklist ids.");
			return;
		}
		String cleanNote = note == null ? "" : note.replaceAll("[\\r\\n]+", " ").trim();
		if (cleanNote.length() > MAX_NOTE)
		{
			cleanNote = cleanNote.substring(0, MAX_NOTE);
		}
		String line = command + " " + checklist + " " + item + " " + result + (cleanNote.isEmpty() ? "" : " " + cleanNote);
		clientThread.invoke(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				SwingUtilities.invokeLater(() -> panel.status("Log in to send reports."));
				return;
			}
			String typed = client.getVarcStrValue(CHATBOX_TYPED_TEXT);
			if (typed != null && !typed.isEmpty())
			{
				SwingUtilities.invokeLater(() -> panel.status("Clear what you've typed in the chatbox first."));
				return;
			}
			executor.execute(() -> type(line));
		});
	}

	private void type(String text)
	{
		try
		{
			for (char c : text.toCharArray())
			{
				int code = KeyEvent.getExtendedKeyCodeForChar(c);
				dispatch(new KeyEvent[]{
					key(KeyEvent.KEY_PRESSED, code, c),
					key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, c),
					key(KeyEvent.KEY_RELEASED, code, c)});
				Thread.sleep(5);
			}
			dispatch(new KeyEvent[]{
				key(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n'),
				key(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\n'),
				key(KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, '\n')});
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		catch (Exception e)
		{
			log.warn("Test Checklists couldn't send a report", e);
			SwingUtilities.invokeLater(() -> panel.status("Couldn't send the report: " + e.getMessage()));
		}
	}

	private KeyEvent key(int id, int code, char c)
	{
		Canvas canvas = client.getCanvas();
		return new KeyEvent(canvas, id, System.currentTimeMillis(), 0, code, c);
	}

	private void dispatch(KeyEvent[] events) throws Exception
	{
		Canvas canvas = client.getCanvas();
		SwingUtilities.invokeAndWait(() ->
		{
			for (KeyEvent event : events)
			{
				canvas.dispatchEvent(event);
			}
		});
	}

	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0x3C, 0xB3, 0x71));
		g.fillRoundRect(1, 1, 14, 14, 4, 4);
		g.setColor(Color.WHITE);
		g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
		g.drawString("✓", 3, 12);
		g.dispose();
		return image;
	}
}
