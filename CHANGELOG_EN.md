# Changelog

## v1.5.0

### New Features

- **RAG knowledge base retrieval (`[KNOWLEDGE]` tag)** — The AI can now proactively consult the mod's built-in Minecraft knowledge base (blocks, mobs, commands, game mechanics, etc.). When precise information is needed, it names documents via a `[KNOWLEDGE]doc name[/KNOWLEDGE]` tag to read their body text, instead of relying solely on fixed content hardcoded into the system prompt. Added three new config options: `rag_enabled` (on by default), `rag_max_docs` (default 8), and `rag_max_chars` (default 20000), controlling whether retrieval is enabled and the per-request document count/character limits. Knowledge base docs are released to `ai-helper/knowledge/` on first launch (English only, to keep jar size down; the AI can read English docs and still answer in the player's language).
- **Structure format conversion (NBT / Litematic / TXT, any direction)** — The unified structure browser now has a "Convert" entry point that can batch-convert `.nbt`, `.litematic`, and `.txt` (MCBLUEPRINT v2) files into any target format. Supports selecting multiple source files and a target directory; conversion fully preserves block states, container items (chests, barrels, etc.), and sign front/back text (including legacy pre-1.20 `Text1`–`Text4` compatibility).

### Improvements

- **Trimmed the system prompt; redstone knowledge now comes from the knowledge base** — Removed roughly 500 lines of hardcoded redstone block/circuit reference material from `AICommandExecutor`, replaced with a pointer telling the AI to consult the knowledge base instead. Also added missing 1.20.4 blocks (TNT, daylight detector, sculk sensor) and a new redstone circuit/logic gate design doc to the knowledge base.
- **Display language now automatically follows the game language** — Removed the `language` config option and its manual-switch entry point: the client reads Minecraft's current game language in real time and applies it immediately, while dedicated servers always use English. The "Mod Language Settings" screen is now a static page that only explains this behavior, with no switch buttons.
- **Fixed filename sanitization logic** — Added `ModPaths.sanitizeFileName`, which only filters characters truly disallowed by the filesystem (`\ / : * ? " < > |` and control characters), instead of replacing Chinese, Japanese, and other Unicode characters or spaces with underscores. Applied consistently across AI-generated files, selection exports, and TXT exports.

### Fixes

- **Knowledge base content corrections** — Fixed the comparator/hopper/dropper container signal strength formula (the previous simplified `items/5` approximation was replaced with the standard `1+floor(14×average fill ratio)` formula), and fixed an incorrect direction rule for note block instruments (determined by the block below, not above).
- **Lost sign text during conversion** — Fixed an issue where blank lines in the middle of sign text were mistakenly treated as the end of a section during NBT/Litematic-to-TXT conversion, causing subsequent lines to be lost. The conversion logic also gained support for extracting and writing container items and sign front/back text (including legacy formats).
- **Remaining hardcoded Chinese text** — Fixed leftover hardcoded Chinese strings in the `/aipos` command echo and the NBT structure summary; filled in missing keys in `zh_cn.json` so it fully matches `en_us.json`.

### Tests

- Added `KnowledgeBaseTest`, `NbtToTxtConverterTest`, `StructureFormatConverterTest`, and `LitematicParserTest` unit tests, covering knowledge base retrieval, structure format conversion, and Litematic parsing.

---

## v1.4.2

### Security & Permission Model Changes

- **`execute_command` no longer executes with elevated server-side privileges** — Vanilla commands suggested by the AI are no longer executed directly on the server with OP permissions. Instead, they are sent to the requesting player's client, which automatically opens the chat screen with the command pre-filled. Whether the command is sent, and with what permission level, is entirely up to the player, removing the risk of "remote backend control of privileged command execution." The server-side command blacklist has been removed accordingly, since the server no longer executes commands directly.
- **New standalone AI Permission Settings screen** — Split out from AI Chat Settings into `K` → **AI Permission Settings**, centralizing the following permission-related options:
  - **Allow AI to Use Vanilla Commands** (`vanilla_commands_enabled`, on by default): when off, the system prompt no longer includes `execute_command` instructions, so the AI can only use the mod's built-in features; even if the AI still tries to generate that instruction, it is rejected outright at runtime.
  - **Require Confirmation Before Execution** (`confirm_before_execute_enabled`, off by default): when on, before the AI uses a mod-specific feature (placing blocks, building blueprints, terrain queries, web search, web scraping, etc.) it first sends a [Yes]/[No] confirmation message in chat. Multiple actions within the same round are merged into a single batch confirmation, and unconfirmed requests are automatically canceled after 60 seconds.
  - **Max tool-call rounds** (the `max_tool_rounds` setting, moved here from the chat settings screen).
- **New `/aiconfirm` command** — Handles [Yes]/[No] button clicks in chat for pending AI action requests. Once confirmed, the conversation automatically resumes, covering all tool types (web search, web scraping, terrain queries, in-game actions), preventing the conversation from stalling or actions from being silently dropped after confirmation.

### Improvements

- **Removed hard block-count caps for fill/clear/terrain queries** — Replaced with soft prompt-level guidance (fill/clear recommended to stay under ~10,000 blocks, terrain queries under ~30,000 blocks in volume). Exceeding these is no longer rejected with an error; instead the AI is prompted to split the operation into multiple calls, reducing unnecessary failures.
- **Removed the teleport tool** — The AI-triggerable teleport instruction has been removed, simplifying the set of available actions.
- **Refactored the multi-round tool loop** — Introduced a unified finishing mechanism (`ToolLoopFinisher`) ensuring the confirmation flow and network calls (web search, web scraping, resuming the AI) all run on separate threads instead of blocking the server's main tick thread.

### Documentation

- Synced the README, user manuals (Chinese & English), and in-game manuals with the latest behavior: updated the `execute_command` permission model description, added documentation for the new permission settings screen, the `/aiconfirm` command, and new config options; removed outdated teleport references; bumped the version number to 1.4.2.

---

## v1.4.1

### New Features

- **Multi-round AI tool calls (agentic loop)** — The AI can now call tools across multiple consecutive rounds within a single reply, querying the environment first and then deciding on further actions based on the results, enabling more complex, coherent tasks.
- **Terrain query tool `[QUERY_REGION]`** — The AI can proactively query the terrain and block distribution of a specified region to understand the site before building, leading to better placement and integration with existing terrain.
- **`find_player` instruction** — The AI can retrieve a player's current coordinates, useful for anchoring a build's origin and relative offsets.
- **Custom blueprint placement origin** — Blueprints now support a specified placement origin, either as an offset relative to the player's facing direction or as absolute coordinates.
- **Pre-placement edit screen** — A confirmation screen now appears before placing a structure, letting you edit the origin coordinates beforehand.
- **Air block support** — Air can now be saved and placed as a regular block entry in blueprints, useful for clearing space and precise shaping.
- **Ignore-by-block-type option in selection analysis** — The selection analysis tool now supports ignoring specific block types, making exports and statistics more focused.
- **Debug toggle: print tool return values** — Added a debug toggle to print tool return values to chat, helping diagnose AI tool calls.

### Improvements

- **Unified block update after structure placement** — After a structure is placed, a single block update pass now runs over all placed blocks, cleaning up invalid placements and recalculating redstone and orientation-dependent connection states, reducing broken blocks after placement.
- **Improved building-generation prompt** — Refined rules for entrance/ground alignment and bed head/foot placement, making AI-generated buildings more consistent with actual game logic.

### Fixes

- **V2 blueprint negative coordinate parsing** — Fixed an issue where V2 blueprints failed to correctly parse negative coordinates.
- **Lost command execution results in streaming mode** — Fixed an issue where instruction execution results could be lost when streaming output was enabled.
- **Hardcoded origin in AI-generated blueprints** — The `# origin:` header line is now stripped before saving AI-generated blueprints, preventing the origin from being hardcoded into the file.

### Documentation

- Added the English user manual `USER_MANUAL_EN.md` and a version badge in the README.

---

## v1.4.0

### New Features

- **OpenAI-compatible API support** — Added `api_format=auto|openai|anthropic`; `auto` automatically detects the interface format based on configuration. OpenAI mode automatically appends the `/chat/completions` endpoint and adapts authentication, request, image, and streaming response formats accordingly.
- **Litematica file support** — The structure browser and `/ainbt` command now support browsing, viewing info for, and placing `.litematic` files, stored in `ai-helper/structures/litematic/`.
- **Litematica selection export** — The selection export screen now has an "export `.litematic`" option to save the current selection in Litematica format.
- **Unified structure browser** — `K` → "Load Structure" is now a single browser that can browse directories, search, preview, delete, and place `.nbt`, `.txt`, and `.litematic` files.
- **UI localization and language switching** — UI strings have been moved into Chinese and English language resource files and are displayed according to the language setting; with the optional `debug-menu` installed, the AI Builder language can be switched from its menu.
- **Mod icon** — Added a display icon for the mod.

### Breaking Changes

- **Unified structure directory** — All structure files are now stored under `ai-helper/structures/`, using `nbts/`, `txts/`, and `litematic/` subdirectories for their respective formats.
- **Adjusted default configuration** — Newly generated configs now default to `screenshot_enabled=false`, `stream_output_enabled=true`, `language=en_us`, and `api_format=auto`.
- **Rebindable keybinding** — The default `K` key can now be rebound via Minecraft's native key binding settings.
- **Removed `/ai build <name>`** — Removed the command-line entry point for directly executing blueprint construction; blueprint building is now triggered through AI conversation instead.
- **Log forwarding migrated to debug-menu** — Removed the `/ailog` command; the log-forwarding toggle and minimum level setting are now provided by the optional `debug-menu` mod.
- **Test logging migrated to debug-menu** — Removed the `/aitest` command; test log generation is now provided by the optional `debug-menu` debug menu.

### Documentation & Release Assets Sync

- **Comprehensive manual calibration against the actual implementation** — Updated the root Chinese manual and the in-game Chinese/English manuals to correct actual commands, structure entry points, file paths, config defaults, and API format descriptions.
- **Config and hot-reload documentation calibration** — Listed the actual available `/aiconfig` subcommands and clarified how to hot-reload configuration after manual edits.
- **`/ainbt` usage calibration** — Clarified that the command only provides `list`, `info`, `all`, and `place` subcommands, with no argument-less GUI.
- **Security boundary clarification** — AI command execution is no longer described as "arbitrary vanilla commands": dangerous server administration, ban, kick, and stop/save commands are rejected.
- **RAG material status clarification** — Marked `rag_plan/` as an unimplemented design and offline candidate material set, avoiding the misunderstanding that the mod currently auto-loads or retrieves from a knowledge base.
- **Release asset updates** — Unified the version number to 1.4.0 and synced the README and Modrinth listing.

---

## v1.3.1

### New Features

- **Sign text preserved in TXT export** — When exporting a selection to TXT format, the front and back text of signs is now fully preserved (`sign_text: front/back`).
- **API key validity check** — The API key is now automatically checked for validity when entering a world and after saving settings, catching configuration errors early.

### Improvements

- **Container contents export enabled by default** — Removed the container-contents selection toggle from the TXT export screen; exports now always include item contents of chests, hoppers, and other containers, simplifying the workflow.

---

## v1.3.0

### New Features

- **`/aistop` command** — Cancels an in-progress AI reply from the command line, without needing to open the chat screen and click a button.
- **`/aiconfig reload` command** — Hot-reloads the config file after manual edits, without restarting the game.
- **`/aiconfig web_search <on/off>` command** — Quickly toggles the web search feature.
- **`/ailog level <error|warn|info|debug>` command** — Customizes the minimum log level shown, useful for debugging.
- **`/aitest` command** — Generates test logs to quickly verify the log-forwarding system is working correctly.
- **TXT export (with container contents)** — The selection tool can now export to `.txt` format while preserving item contents of chests, hoppers, and other containers.
- **AI can execute vanilla commands** — The AI can now execute vanilla Minecraft commands via the `execute_command` instruction type (subject to a safety blacklist).

### Improvements

- **Command documentation categorization** — Commands in the built-in user manual are now grouped into four categories (Core/Configuration/Tools/NBT) for easier reference.
- **Blueprint format documentation additions** — Added documentation for the `# origin:` field and coordinate axis direction comments.
- **NBT command-line usage additions** — Clarified the usage of `/ainbt list`, `/ainbt info`, and `/ainbt place`, including how to write paths containing spaces.
- **AI-generated blueprint storage path** — Clarified that AI-generated blueprints are automatically saved to the `txts/ai-generated/` subdirectory.
- **Config hot-reload** — No restart needed anymore; `/aiconfig reload` applies manually edited configuration.
- **Default language** — Changed the default UI language to `en_us`.

### Documentation Updates

- Updated the Chinese and English built-in user manuals to version 1.3.0.
- Added FAQ entries such as "How do I cancel an AI reply?" and "Do I need to restart after changing the config?"
- Added safety notes and performance recommendations.

---

## v1.2.0

- AI chat screen (streaming output, multi-turn conversation, blueprint file references)
- NBT structure browser (GUI)
- TXT blueprint management screen
- Selection tool (highlight rendering, analysis, NBT/blueprint export)
- Web search (Tavily API) and web page scraping
- Screenshot analysis
- Bilingual Chinese/English support
- V2 blueprint format (MCBLUEPRINT v2)
