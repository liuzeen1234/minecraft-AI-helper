# Modrinth Listing Information

> Content below is ready to be used directly on the Modrinth project creation/edit page.

---

## Basic Information

| Field | Value |
|-------|-------|
| **Name** | AI Builder |
| **Slug (URL)** | `ai-builder` |
| **Summary** | In-game AI assistant with natural-language chat, AI-powered building, structure browsing, selection export, and web search. |
| **Categories** | Utility, Management |
| **License** | MIT |
| **Client/Server Side** | Both (Client + Server) |

---

## Version Information

| Field | Value |
|-------|-------|
| **Version** | 1.4.0 |
| **Minecraft Version** | 1.20.4 |
| **Mod Loader** | Fabric |
| **Fabric Loader Version** | >= 0.15.0 |
| **Java Version** | >= 17 |
| **Dependencies** | Fabric API (required) |

---

## Project Description (Markdown)

Paste the following directly into the Modrinth Description editor:

```markdown
# AI Builder

A feature-rich Fabric mod that integrates AI conversational capabilities into Minecraft. Chat with AI using natural language, have it build structures, manage NBT/Litematica/TXT files, export selections, and let AI search the web for information — all from within the game.

## Features

### AI Chat and Command Execution
- **`/ai <message>`** — Chat with AI directly in-game
- AI executes in-game operations via natural language:
  - Place, fill, or clear blocks
  - Give items, spawn entities
  - Set time/weather, teleport players
  - Execute supported Minecraft commands at permission level 2 (unsafe administration, moderation, and stop/save root commands are rejected)
- **Multi-turn conversation memory** — AI remembers context across messages
- **Screenshot support** — AI can analyze your game screen and respond accordingly
- **`/aistop`** — Terminate AI response via command (no need to open chat UI)
- **`/aiconfig reload`** — Hot-reload configuration without restarting the game
- **API Key validation** — Automatically verifies API key on world entry and settings save

### Structure Management
- **Unified Structure Browser** — Press `K` → Load Structures to browse, search, delete, and place `.nbt`, `.litematic`, and `.txt` files with folder navigation
- **NBT/Litematica Placement** — Preserves block states, block-entity data, and structure entities while skipping `air` and `structure_void`
- **TXT Blueprint System** — Supports two blueprint formats:
  - V1: Character grid with legend mapping (human-readable)
  - V2 (MCBLUEPRINT v2): Explicit coordinates with full block state properties (precise reproduction)
- **`/ai blueprints`** — List all available blueprints

### Selection Tools
- Two-point graphical selection interface with real-time highlight rendering
- **Selection Analysis** — Count block types and quantities within a selected region
- **Export to NBT** — Save selection as `.nbt` file (preserves block entity data such as chest contents and sign text; includes decorative entities like armor stands, item frames, and paintings)
- **Export to Blueprint** — Export selection as V2 format blueprint text (includes container inventories and sign text by default)

### Web Search
- **Web Search** — AI searches for the latest information via Tavily API
- **Web Scraping** — AI can fetch content from specified URLs
- Automatically generates building commands based on web content

### Configuration
- **In-game Settings UI** — Press `K` to open; toggle screenshot, conversation memory, and web search
- **`/aiconfig`** — Configure API URL, key, and model via command
- **`/ainew`** — Clear conversation history and start fresh

## Commands

| Command | Description |
|---------|-------------|
| `/ai <message>` | Chat with AI |
| `/ai blueprints` | List all blueprints |
| `/ai reload_blueprints` | Reload blueprint files |
| `/ainew` | Clear conversation history |
| `/aistop` | Terminate AI response |
| `/aiconfig show` | View current configuration |
| `/aiconfig reload` | Hot-reload configuration |
| `/aiconfig web_search <on/off>` | Toggle web search |
| `/aiconfig api_base_url/api_key/model <value>` | Update the supported API settings |
| `/aiconfig web_search <on/off>` | Toggle web search |
| `/aiconfig tavily_api_key <value>` | Update the Tavily API key |
| `/aipos` | Show current coordinates |
| debug-menu "Generate Test Logs" | Generate test log entry (moved from `/aitest` into the debug-menu menu, default key M) |
| `/ainbt list/info/all/place` | Manage NBT and Litematica structures; the GUI is opened through `K` → Load Structures |

## Configuration

A configuration file is generated at `ai-helper/config/ai-builder.properties` on first launch:

| Key | Description | Default |
|-----|-------------|---------|
| `api_base_url` | AI API endpoint | (OpenAI-compatible endpoint) |
| `api_key` | API key | Must be set manually |
| `model` | AI model name | (configurable) |
| `screenshot_enabled` | Screenshot feature | false |
| `context_enabled` | Multi-turn conversation | true |
| `web_search_enabled` | Web search | true |
| `tavily_api_key` | Tavily search API key | (empty) |
| `stream_output_enabled` | Incremental streaming output | true |
| `language` | Interface language (zh_cn / en_us) | en_us |
| `api_format` | auto, openai, or anthropic API format | auto |

> You must configure your own AI API key before use. OpenAI-compatible and Anthropic Messages APIs are supported; `api_format=auto` selects the format from the endpoint.

## File Structure

- `ai-helper/structures/nbts/` — Store `.nbt` structure files (supports subfolders)
- `ai-helper/structures/litematic/` — Store `.litematic` structure files
- `ai-helper/structures/txts/` — Store `.txt` blueprint files
- `ai-helper/structures/txts/ai-generated/` — AI-generated blueprint files (auto-saved here)
- `ai-helper/config/ai-builder.properties` — Mod configuration file

## Keybinds

| Key | Function |
|-----|----------|
| `K` | Open mod settings |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) (>= 0.15.0)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the mod JAR into your `.minecraft/mods/` directory
4. Launch the game, press `K` to open settings, or use `/aiconfig` to set your API key
```

---

## Pre-Upload Checklist

- [ ] Run `./gradlew build` to generate the JAR
- [ ] Find `ai-builder-1.4.0.jar` in `build/libs/` (do NOT upload the `-sources.jar`)
- [ ] Prepare a 512x512 mod icon (PNG)
- [ ] Prepare 2-4 in-game screenshots showing:
  - AI chat interface
  - NBT structure browser
  - Selection tools in action
  - AI-built structure result
- [ ] Register a Modrinth account
- [ ] Create a GitHub repository and push source code (recommended by Modrinth)

---

## Modrinth Compliance Notes

- Description is fully in English (Modrinth rule 2.2 requires English unless the project is language-specific)
- Plain-text description is provided alongside formatted content (rule 2.2 accessibility)
- Summary does not repeat the project title and contains no formatting (rule 5)
- Project title contains only the mod name without filler (rule 5)
- All metadata (license, side, tags) is filled out correctly (rule 5)
- No prohibited content; mod does not upload data to remote servers without disclosure (the AI API call is user-configured and clearly documented)
- Not a cheat/hack — provides no unfair multiplayer advantage (rule 3)
