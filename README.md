# Test Checklists

A RuneLite plugin for private-server play-testing. Your testers get a side panel listing what needs
testing. They mark each item Pass or Fail, add a note if they want, and the result goes straight to
your game server under the account they're logged in on.

- Checklists come from a JSON file you publish anywhere over HTTP(S).
- Reports are sent as an ordinary chat command (`::testreport ...` by default), so your server needs
  only one command handler. No extra ports, accounts or API keys.
- The plugin remembers each account's results, so testers can pick up where they left off.
- Optionally, the panel stays hidden until your server tells an account it is a tester.

## Install

Pick whichever fits your client:

1. **Side-load the jar.** Download `test-checklists-<version>.jar` from
   [Releases](../../releases) and put it in `~/.runelite/sideloaded-plugins/`. Side-loaded plugins
   need RuneLite's developer mode (`--developer-mode`).
2. **Build it into your own client.** Copy `src/main/java/com/testchecklists` into your RuneLite
   fork. To have RuneLite load it like a built-in plugin, change the package to one under
   `net.runelite.client.plugins` (for example `net.runelite.client.plugins.testchecklists`).
   RuneLite loads every plugin in that package from the classpath at startup.

Build from source with `./gradlew jar`. The jar lands in `build/libs/`.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Checklist feed URL | *(empty)* | Where your `checklists.json` lives. Nothing loads until this is set. |
| Report command | `::testreport` | The chat command the plugin types. |
| Enable message | *(empty)* | When set, the panel appears only after the server sends this exact game message (send it at login to testers). When empty, the panel is there for everyone who has the plugin. |
| Refused message | `Only testers can send test reports.` | The game message your server sends when an account may not report. The panel shows a clear note when it sees it. |

If you ship the plugin built into your own client, set your defaults in `TestChecklistsConfig`.

## The feed

A single JSON document. See [`example/checklists.json`](example/checklists.json).

```json
{
  "generated": "2026-10-25 12:00",
  "checklists": [
    {
      "id": "bank-buttons",
      "title": "Bank buttons",
      "area": "Interfaces",
      "source": "build 1.4",
      "setup": "Stand next to any bank.",
      "notes": "Optional free text shown under the title.",
      "items": [
        { "n": 1, "text": "Open your bank and press Deposit inventory.", "expect": "Everything in your inventory goes into the bank." },
        { "n": 2, "text": "Press Deposit worn items.", "expect": "Your equipment goes into the bank.", "passed": true }
      ]
    }
  ]
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `generated` | no | Shown as "updated ..." in the panel. |
| `checklists[].id` | yes | Letters, digits, `-` and `_` only. Sent back in reports. |
| `checklists[].title` | yes | Shown in the picker. |
| `checklists[].area` | no | Shown before the title, for grouping. |
| `checklists[].source` | no | Where the work came from (a build, an issue). |
| `checklists[].setup` | no | Shown as "Before you start:". |
| `checklists[].notes` | no | Extra text under the title. Newlines are kept. |
| `items[].n` | yes | The item number, sent back in reports. |
| `items[].text` | yes | What to do. |
| `items[].expect` | no | Shown as "You should see:". |
| `items[].passed` | no | `true` shows "Passed earlier" until this account records its own result. |

The plugin re-reads the feed when the panel opens and when the tester presses Refresh. Serve it
with a short cache lifetime if you update it often.

## The server side

Your game server needs one chat command and two replies.

**1. The command.** When a tester presses Pass or Fail, the plugin types this into the chatbox:

```
::testreport <checklist id> <item number> <pass|fail> [note...]
```

The note is optional, single-line and at most 200 characters. The plugin won't type over a
half-written chat message; it asks the tester to clear the chatbox first.

**2. The confirmation.** After storing the report, send the player this game message, exactly:

```
Test report recorded: <checklist id> #<item number> <pass|fail>.
```

The plugin marks the item Passed or Failed when it sees this line. If no confirmation arrives,
nothing is marked, so a tester can tell when a report didn't land.

**3. Refusals and the enable message (optional).**

- If an account may not report, send the "Refused message" from the settings.
- To show the panel only to testers, send the "Enable message" when a tester logs in. Everyone
  else never sees the panel.

A minimal handler, in pseudocode:

```
on command "testreport" (player, args):
    if not player.isTester: player.message("Only testers can send test reports."); return
    checklist, item, result = args[0], int(args[1]), args[2]
    if result not in ("pass", "fail"): player.message("Usage: ::testreport <checklist> <item> <pass|fail> [note]"); return
    note = join(args[3:], " ")
    append_json_line("test-reports/<date>.jsonl",
        {time, account: player.name, checklist, item, result, note, position: player.coords})
    player.message("Test report recorded: " + checklist + " #" + item + " " + result + ".")
```

Store the reports however suits you: a JSON-lines file, a database table, a Discord webhook.

## How reports are sent

RuneLite plugins can't send chat commands directly, so the plugin types the command into the game
canvas as key events, then presses Enter. This is why it waits for an empty chatbox. It works on
any server that accepts the command from the normal chat input.

## License

BSD 2-Clause, see [LICENSE](LICENSE).
