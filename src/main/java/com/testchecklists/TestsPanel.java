package com.testchecklists;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.CompoundBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** The side panel: pick a checklist, then Pass/Fail each item with an optional note. */
class TestsPanel extends PluginPanel
{
	private static final Color PASS = new Color(0x3C, 0xB3, 0x71);
	private static final Color FAIL = new Color(0xD9, 0x53, 0x4F);
	/** HTML labels wrap at this width instead of stretching the panel (PANEL_WIDTH less borders, padding and scrollbar). */
	private static final String WRAP = "<html><body style='width: " + (PANEL_WIDTH - 60) + "px'>";

	private final TestChecklistsPlugin plugin;
	private final JComboBox<Feed.Checklist> picker = new JComboBox<>();
	private final JLabel status = new JLabel(" ");
	private final JPanel items = new JPanel();
	/** Each item's result label, by "checklist.item". */
	private final Map<String, JLabel> results = new HashMap<>();

	TestsPanel(TestChecklistsPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 6));

		JPanel top = new JPanel(new BorderLayout(4, 4));
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(e -> plugin.refresh());
		top.add(picker, BorderLayout.CENTER);
		top.add(refresh, BorderLayout.EAST);
		top.add(status, BorderLayout.SOUTH);
		picker.addActionListener(e -> showChecklist((Feed.Checklist) picker.getSelectedItem()));

		items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
		add(top, BorderLayout.NORTH);
		add(items, BorderLayout.CENTER);
	}

	void status(String text)
	{
		status.setText("<html>" + escape(text) + "</html>");
	}

	void setFeed(Feed feed)
	{
		picker.removeAllItems();
		if (feed == null || feed.checklists == null || feed.checklists.isEmpty())
		{
			status("Nothing to test right now.");
			showChecklist(null);
			return;
		}
		for (Feed.Checklist c : feed.checklists)
		{
			picker.addItem(c);
		}
		status(feed.checklists.size() + " checklists (updated " + feed.generated + ")");
	}

	void recorded(String checklist, int item, String result)
	{
		JLabel label = results.get(checklist + "." + item);
		if (label != null)
		{
			mark(label, result);
		}
		status("Recorded " + checklist + " #" + item + ": " + result + ".");
	}

	private void showChecklist(Feed.Checklist c)
	{
		items.removeAll();
		results.clear();
		if (c != null)
		{
			StringBuilder head = new StringBuilder(WRAP + "<b>" + escape(c.title) + "</b><br>" + escape(c.area));
			if (c.source != null && !c.source.isEmpty())
			{
				head.append(" &middot; ").append(escape(c.source));
			}
			if (c.setup != null && !c.setup.isEmpty())
			{
				head.append("<br><b>Before you start:</b> ").append(escape(c.setup));
			}
			if (c.notes != null && !c.notes.isEmpty())
			{
				head.append("<br>").append(escape(c.notes).replace("\n", "<br>"));
			}
			JLabel header = new JLabel(head.append("</html>").toString());
			header.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			items.add(header);
			for (Feed.Item item : c.items)
			{
				items.add(card(c, item));
			}
		}
		items.revalidate();
		items.repaint();
	}

	private JPanel card(Feed.Checklist c, Feed.Item item)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		// BoxLayout lines children up by alignmentX; mixing a label's default LEFT with a panel's CENTER
		// pushed the text half a panel to the right, so everything here is LEFT.
		JLabel text = new JLabel(WRAP + "<b>" + item.n + ".</b> " + escape(item.text)
			+ (item.expect == null || item.expect.isEmpty() ? "" : "<br><i>You should see: " + escape(item.expect) + "</i>") + "</html>");
		text.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(text);

		JLabel noteLabel = new JLabel("Note (optional):");
		noteLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noteLabel.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(noteLabel);

		JTextField note = new JTextField();
		note.setToolTipText("Sent with the result: what you saw, and where");
		note.setBackground(ColorScheme.DARK_GRAY_COLOR);
		note.setForeground(Color.WHITE);
		note.setCaretColor(Color.WHITE);
		note.setBorder(new CompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 4, 3, 4)));
		note.setAlignmentX(Component.LEFT_ALIGNMENT);
		note.setMaximumSize(new Dimension(Integer.MAX_VALUE, note.getPreferredSize().height));
		card.add(note);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton pass = new JButton("Pass");
		JButton fail = new JButton("Fail");
		JLabel result = new JLabel(" ");
		pass.addActionListener(e -> plugin.report(c.id, item.n, "pass", note.getText()));
		fail.addActionListener(e -> plugin.report(c.id, item.n, "fail", note.getText()));
		row.add(pass);
		row.add(fail);
		row.add(result);
		card.add(row);

		String previous = plugin.recordedResult(c.id, item.n);
		if (previous != null)
		{
			mark(result, previous);
		}
		else if (item.passed)
		{
			result.setText("Passed earlier");
			result.setForeground(PASS.darker());
		}
		results.put(c.id + "." + item.n, result);

		JPanel spaced = new JPanel(new BorderLayout());
		spaced.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		spaced.add(card);
		spaced.setAlignmentX(Component.LEFT_ALIGNMENT);
		return spaced;
	}

	private static void mark(JLabel label, String result)
	{
		boolean passed = "pass".equals(result);
		label.setText(passed ? "Passed" : "Failed");
		label.setForeground(passed ? PASS : FAIL);
	}

	private static String escape(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
