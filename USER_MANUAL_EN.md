# AI Builder User Manual

> Version 1.4.1 | Minecraft 1.20.4 | Fabric Mod

## Installation & Requirements

| Item | Requirement |
|---|---|
| Minecraft version | 1.20.4 |
| Mod loader | Fabric Loader ≥ 0.15.0 |
| Java version | ≥ 17 |
| Prerequisite mod | Fabric API (required) |

1. Install Fabric Loader and Fabric API.
2. Drop `ai-builder-1.4.1.jar` into `.minecraft/mods/`.
3. Launch the game and press `K` to open the AI Builder settings.

## First-Time Setup

On first launch, the mod creates `ai-helper/config/ai-builder.properties`. You must set an API key before using the AI.

```text
/aiconfig api_base_url your API URL
/aiconfig api_key your API key
/aiconfig model your model name
```

You can also press `K` → **AI Chat Settings** for a visual configuration. The mod supports OpenAI-compatible interfaces and the Anthropic Messages interface. The default `api_format=auto` picks the format automatically based on the URL; if needed, edit the config file directly to specify `openai` or `anthropic`, then run `/aiconfig reload`.

## Keybindings

| Key | Function |
|---|---|
| `K` (default, rebindable) | Open the mod's main settings menu |
| `Enter` | Send a message in the AI chat screen; place a structure or enter a folder in the structure browser |
| `Escape` | Close the current mod screen |
| `Page Up` / `Page Down`, mouse wheel | Scroll the chat or file list |
| `↑` / `↓`, `Backspace`, `Delete` | Navigate the unified structure browser, go up a level, or delete the selected file |

`K` can be changed under the vanilla **Options → Controls → Key Binds** in the **AI Builder** category. The other keys are fixed in-screen actions.

## Commands

### AI & Tools

| Command | Description |
|---|---|
| `/ai <message>` | Chat with the AI; the AI can perform building actions via supported instructions in its reply |
| `/ai blueprints` | List loaded TXT blueprints |
| `/ai reload_blueprints` | Reload TXT blueprints |
| `/ai test_stairs` | Place a sample of stairs for debugging orientation |
| `/ainew` | Clear conversation history and start a new topic |
| `/aistop` | Abort the current AI request |
| `/aipos` | Show current coordinates and dimension |

### Configuration

| Command | Description |
|---|---|
| `/aiconfig show` | Show API, model, web search, and Tavily configuration |
| `/aiconfig api_base_url <value>` | Set the API URL |
| `/aiconfig api_key <value>` | Set the API key |
| `/aiconfig model <value>` | Set the model name |
| `/aiconfig web_search <on/off>` | Enable or disable web search |
| `/aiconfig tavily_api_key <value>` | Set the Tavily API key |
| `/aiconfig reload` | Reload the config file after manual edits |

`/aiconfig` is not a general "arbitrary key-value" command. Set `screenshot_enabled`, `context_enabled`, `stream_output_enabled`, and `language` in the settings screen, and set `api_format` by editing the config file directly and then reloading.

### Structure Commands

| Command | Description |
|---|---|
| `/ainbt list` | List NBT and Litematica structure files |
| `/ainbt info <filename>` | Show details of an NBT or Litematica structure |
| `/ainbt all` | Show a summary of all structures |
| `/ainbt place <filename>` | Place an NBT or Litematica structure at the player's feet |

`/ainbt` has no argument-less GUI. For graphical structure management, use `K` → **Load Structure**.

> The in-chat log display and "generate test log" are provided by the optional debug-menu mod and are not AI Builder commands.

## Features in Detail

### AI Chat & Building

Press `K` → **AI Chat**, or use `/ai <message>`. The chat screen supports up to 20 messages (10 rounds) of context, up to 1024 characters of input, TXT blueprint references, clearing history, canceling requests, and incremental streaming display. The screenshot feature is off by default; when enabled, it captures a scaled game frame when you send a message.

The AI can place, fill, or clear blocks, give items, spawn entities, set time/weather, teleport, and generate/place blueprints. A single fill or clear is limited to 10,000 blocks, giving is limited to 64 items, and spawning is limited to 20 entities. `execute_command` never executes anything automatically — the AI can only pre-fill a suggested command into your chat input box; you must review it yourself and press Enter to actually send it, and whatever permission you already have in-game is what applies. Do not describe it as able to run commands automatically or with elevated permissions.

Blueprint coordinates are relative: X is east, Y is up, Z is south, and the origin is at the player's feet. Both V1 and MCBLUEPRINT v2 TXT formats can be loaded.

### Unified Structure Browser

Press `K` → **Load Structure** to browse, search, delete, and place the following files, with subfolder support:

- `ai-helper/structures/nbts/`: standard `.nbt` structures;
- `ai-helper/structures/litematic/`: `.litematic` structures;
- `ai-helper/structures/txts/`: V1/V2 `.txt` blueprints.

NBT/Litematica placement skips `air` and `structure_void`, and preserves block states, block entity data, and structure entities; legacy sign data is converted to the 1.20+ format. AI-generated TXT blueprints are saved to `ai-helper/structures/txts/ai-generated/`.

### Selection Tool

Press `K` → **Selection Tool**. Set two opposite corner coordinates (or use the current position) and confirm; the game then displays a highlight box. The draft is preserved when you close the screen. The analyze/export screen can count blocks and export on the server side:

- **TXT / MCBLUEPRINT v2**: always includes container items and the front/back text of non-empty signs;
- **NBT**: preserves block entity data;
- **Litematica**: preserves block entity data, with an option to include entities.

### Web Search, Web Scraping & Screenshots

Web search requires `web_search_enabled=true` and a configured `tavily_api_key`. When the AI decides a search is needed, it fetches up to 5 Tavily results; the search timeout is 120 seconds. The AI can also scrape a specified web page based on tags in its reply; the scrape timeout is 30 seconds and the page text is truncated to at most 8,000 characters.

With `screenshot_enabled` on, sending a message in the chat screen captures a screenshot 2 ticks after the screen closes; the image has a maximum width of 512 px and is temporarily saved at `ai-helper/screenshots/ai_chat_temp.png`. The `/ai` screenshot path is `ai_temp.png`.

## Configuration Options

Config file: `ai-helper/config/ai-builder.properties`

| Option | Default | Description |
|---|---|---|
| `api_base_url` | `https://api.kimi.com/coding/v1/messages` | API endpoint |
| `api_key` | `your-api-key-here` | API key, must be set |
| `model` | `kimi-for-coding` | Model name |
| `screenshot_enabled` | `false` | Whether to send screenshots in AI chat |
| `context_enabled` | `true` | Whether to enable multi-round conversation context |
| `web_search_enabled` | `true` | Whether to allow Tavily web search |
| `tavily_api_key` | empty | Tavily API key |
| `stream_output_enabled` | `true` | Whether to display AI replies incrementally |
| `language` | `en_us` | UI language: `zh_cn` or `en_us` |
| `api_format` | `auto` | `auto`, `openai`, or `anthropic` |

Press `K` → **AI Chat Settings** to change the four boolean toggles; the API settings screen can change the API URL, key, model, and Tavily key. After editing any config manually, use `/aiconfig reload` to apply. Language can be switched under `K` → **Mod Language Settings**, but on the next client launch it will follow the current Minecraft game language again.

## Blueprint Format

V2 is recommended:

The following example is based on `run/ai-helper/structures/txts/example2.txt` in the run directory; set the blueprint name to `example` and leave everything else unchanged:

```text
# MCBLUEPRINT v2
# name: example
# size: 5x6x5
# origin: 0,0,0
# The origin is at the lowest layer of the structure's northwest corner; x is east, y is up, z is south
# Format: x,y,z  block_id  [key=value ...]

## BLOCKS

# --- Layer 1 (y=0) ---
2,0,2   oak_log   axis=y
3,0,2   short_grass
0,0,3   short_grass
2,0,4   short_grass
4,0,4   short_grass

# --- Layer 2 (y=1) ---
2,1,2   oak_log   axis=y

# --- Layer 3 (y=2) ---
0,2,0   oak_leaves   distance=4   persistent=false   waterlogged=false
1,2,0   oak_leaves   distance=3   persistent=false   waterlogged=false
2,2,0   oak_leaves   distance=2   persistent=false   waterlogged=false
3,2,0   oak_leaves   distance=3   persistent=false   waterlogged=false
0,2,1   oak_leaves   distance=3   persistent=false   waterlogged=false
1,2,1   oak_leaves   distance=2   persistent=false   waterlogged=false
2,2,1   oak_leaves   distance=1   persistent=false   waterlogged=false
3,2,1   oak_leaves   distance=2   persistent=false   waterlogged=false
4,2,1   oak_leaves   distance=3   persistent=false   waterlogged=false
0,2,2   oak_leaves   distance=2   persistent=false   waterlogged=false
1,2,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,2,2   oak_log   axis=y
3,2,2   oak_leaves   distance=1   persistent=false   waterlogged=false
4,2,2   oak_leaves   distance=2   persistent=false   waterlogged=false
0,2,3   oak_leaves   distance=3   persistent=false   waterlogged=false
1,2,3   oak_leaves   distance=2   persistent=false   waterlogged=false
2,2,3   oak_leaves   distance=1   persistent=false   waterlogged=false
3,2,3   oak_leaves   distance=2   persistent=false   waterlogged=false
4,2,3   oak_leaves   distance=3   persistent=false   waterlogged=false
1,2,4   oak_leaves   distance=3   persistent=false   waterlogged=false
2,2,4   oak_leaves   distance=2   persistent=false   waterlogged=false
3,2,4   oak_leaves   distance=3   persistent=false   waterlogged=false
4,2,4   oak_leaves   distance=4   persistent=false   waterlogged=false

# --- Layer 4 (y=3) ---
1,3,0   oak_leaves   distance=3   persistent=false   waterlogged=false
2,3,0   oak_leaves   distance=2   persistent=false   waterlogged=false
3,3,0   oak_leaves   distance=3   persistent=false   waterlogged=false
0,3,1   oak_leaves   distance=3   persistent=false   waterlogged=false
1,3,1   oak_leaves   distance=2   persistent=false   waterlogged=false
2,3,1   oak_leaves   distance=1   persistent=false   waterlogged=false
3,3,1   oak_leaves   distance=2   persistent=false   waterlogged=false
4,3,1   oak_leaves   distance=3   persistent=false   waterlogged=false
0,3,2   oak_leaves   distance=2   persistent=false   waterlogged=false
1,3,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,3,2   oak_log   axis=y
3,3,2   oak_leaves   distance=1   persistent=false   waterlogged=false
4,3,2   oak_leaves   distance=2   persistent=false   waterlogged=false
0,3,3   oak_leaves   distance=3   persistent=false   waterlogged=false
1,3,3   oak_leaves   distance=2   persistent=false   waterlogged=false
2,3,3   oak_leaves   distance=1   persistent=false   waterlogged=false
3,3,3   oak_leaves   distance=2   persistent=false   waterlogged=false
4,3,3   oak_leaves   distance=3   persistent=false   waterlogged=false
0,3,4   oak_leaves   distance=4   persistent=false   waterlogged=false
1,3,4   oak_leaves   distance=3   persistent=false   waterlogged=false
2,3,4   oak_leaves   distance=2   persistent=false   waterlogged=false
3,3,4   oak_leaves   distance=3   persistent=false   waterlogged=false

# --- Layer 5 (y=4) ---
2,4,1   oak_leaves   distance=1   persistent=false   waterlogged=false
1,4,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,4,2   oak_log   axis=y
3,4,2   oak_leaves   distance=1   persistent=false   waterlogged=false
2,4,3   oak_leaves   distance=1   persistent=false   waterlogged=false

# --- Layer 6 (y=5) ---
2,5,1   oak_leaves   distance=2   persistent=false   waterlogged=false
1,5,2   oak_leaves   distance=2   persistent=false   waterlogged=false
2,5,2   oak_leaves   distance=1   persistent=false   waterlogged=false
3,5,2   oak_leaves   distance=2   persistent=false   waterlogged=false
2,5,3   oak_leaves   distance=2   persistent=false   waterlogged=false
```

A V2 block line has the format `x,y,z   block_id   [key=value ...]`. `# name:`, `# size:`, and `# origin:` are optional metadata, and lines starting with `#` are comments.

## Directory Layout

```text
.minecraft/
├── ai-helper/
│   ├── config/ai-builder.properties
│   ├── structures/
│   │   ├── nbts/
│   │   ├── litematic/
│   │   └── txts/
│   │       └── ai-generated/
│   └── screenshots/
│       ├── ai_temp.png
│       └── ai_chat_temp.png
└── mods/ai-builder-1.4.1.jar
```

All structure directories support subfolders at any depth.

## FAQ & Notes

**The AI doesn't respond:** Use `/aiconfig show` to check the key and API URL; confirm your network is available. The optional debug-menu can provide chat logs to help troubleshoot.

**How to switch language or streaming output:** Use `K` → **Mod Language Settings** and `K` → **AI Chat Settings** respectively; there is no corresponding `/aiconfig language` or `/aiconfig stream_output_enabled` command.

**After manually editing the config:** Run `/aiconfig reload`; no restart needed.

**Security & performance:** The API key is stored in the local config file. AI operations cannot be undone, so back up important saves first. Web search and web scraping send requests to external services. Placing large structures may cause a brief stutter.

## License

MIT License

**Author:** liuzeen1234  
**Source:** https://github.com/liuzeen1234/minecraft-AI-helper
