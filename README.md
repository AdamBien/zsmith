# zsmith

Zero-dependency AI agent harness with tool execution, SKILL.md and agentic loop support. The entire agent implementation is a single **296 KB** jar, with no external libraries, only the JDK 25. Optionally integrates with [LightMetal](#lightmetal-embedded-local-inference) for fully on-device GGUF inference on Apple Silicon: drop `lightmetal.jar` on the classpath and it is auto-selected, no code or config change required.

![zsmith](zsmith.png)

## Requirements

- **Java 25+**: uses implicit classes, text blocks, records, and source-file mode
- **An LLM**: an **Anthropic API key** (`anthropic.api.key` in `~/.zsmith/app.properties` or as a system property) for the default provider, or any [alternative provider](#alternative-llm-providers): [Amazon Bedrock Mantle](#amazon-bedrock-mantle), an [OpenAI-compatible endpoint](#openai-endpoint) (including a keyless local Ollama, LM Studio, llama.cpp or vLLM), or [LightMetal](#lightmetal-embedded-local-inference) on Apple Silicon for in-process GGUF inference with no key and no network
- **The jar**: `zsinstall` fetches a prebuilt `zbo/zsmith.jar`, no build required. Building from source needs [zb](https://github.com/AdamBien/zb): run `zb.sh` in `zsmith/` (no Maven/Gradle)

## Installation

Fetch the latest prebuilt `zsmith.jar` into `./zbo/` without cloning or building:

```bash
curl -O https://raw.githubusercontent.com/AdamBien/zsmith/main/zsinstall
chmod +x zsinstall
./zsinstall
```

`zsinstall` is a single-file Java 25 script that downloads the latest release asset from GitHub into `./zbo/zsmith.jar`, matching the `-cp zbo/zsmith.jar` shebang used by the example agents. Re-run any time to upgrade.

Based on the [`java-cli-script`](https://airails.dev) skill from [airails.dev](https://airails.dev): single-file, zero-dependency, shebang-launched Java 25 utilities. Optional local inference via [LightMetal](https://github.com/AdamBien/lightmetal), a Java 25 GGUF runner for Apple Silicon's Metal via the Foreign Function & Memory API.

## Quick Start

Install the jar, add your API key, and run your first agent in under a minute.

**1. Install the jar** (see [Installation](#installation)). It is copied to: `./zbo/zsmith.jar`.

**2. Add your Anthropic API key** to `~/.zsmith/app.properties`:

```properties
anthropic.api.key=sk-ant-...
```

**3. Save this as `calculator`** (no file extension) in the same directory as `zbo/`:

```java
#!/usr/bin/java --class-path=zbo/zsmith.jar --source 25

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.boundary.Tools;

void main() {
    var calculator = new Agent("calculator", """
            You are a calculator assistant.
            1. Use the user_question tool to ask the user for a math expression.
            2. Use the calculator tool to evaluate it.
            3. Show the result to the user.
            4. Loop until the user types 'quit'.
            """)
            .withTools(Tools.USER_QUESTION, Tools.USER_MESSAGE, Tools.CALCULATOR);
    calculator.act();
}
```

**4. Make it executable and run it:**

```bash
chmod +x calculator
./calculator
```

The agent asks for a math expression, evaluates it with the `calculator` tool, prints the result, and loops until you type `quit`. No build step: Java 25 runs the script directly against the prebuilt jar.

**Running without an API key.** On Apple Silicon you can skip step 2 entirely: drop [`lightmetal.jar`](#lightmetal-embedded-local-inference) on the classpath and zsmith auto-selects local GGUF inference, with no key and no network. Adjust the shebang in step 3:

```java
#!/usr/bin/java --class-path=zbo/zsmith.jar:lightmetal.jar --enable-native-access=ALL-UNNAMED --source 25
```

The agent code is unchanged. See [LightMetal](#lightmetal-embedded-local-inference) for model configuration.

Once this works, read on for the library API, tool profiles, and configuration.

## Usage

```java
var agent = new Agent("calculator", "You are a helpful assistant.")
        .withTools(new CalculatorTool(), new CurrentTimeTool());

var response = agent.chat("What is 42 * 17?");
```

Agentic execution: `act()` sends `"go"` as the user message, letting the system prompt drive the task:

```java
var agent = new Agent("reporter", "Summarize today's tasks.")
        .withTools(new ReadFileTool(), new CurrentTimeTool());

var response = agent.act();
```

## Tool Profiles

Predefined tool groupings for common use cases:

```java
var agent = new Agent("assistant")
        .withUserIOTools()   // user_message, user_question, user_confirmation
        .withFileIOTools()   // every sandboxed file tool, plus read_any_file and write_any_file
        .withAllTools();     // every tool in the Tools enum
```

`withAllTools()` includes all tools from the `Tools` enum. Sandboxed file tools (`read_file`, `write_file`, `list_files`) require `withFileIOTools()` because they need a configured `sandbox.path`.

> **`withFileIOTools()` is not a sandbox.** It grants the seven sandboxed tools *and* `read_any_file` and `write_any_file`, which reach any absolute path on the filesystem. `withAllTools()` includes those two as well, plus `execute_script`. To confine an agent to one directory, use `withSandbox(root, ...)`, which grants only sandboxed handlers and withholds every unconfined one.

`withSandbox(root, tools...)` grants sandboxed file tools on an explicit root directory instead of the configured `sandbox.path`. Select tools via the `SandboxTools` enum, or omit the selection to grant all of them:

```java
var reviewer = new Agent("reviewer", "You review Java code.")
        .withSandbox(repository, SandboxTools.READ_FILE, SandboxTools.SEARCH_FILES);
```

### Launch App Tool

`withLaunchAppTool()` adds a config-driven tool that launches an external application and passes arguments to it:

```java
// Explicit configuration
var agent = new Agent("assistant")
        .withLaunchAppTool("run_tests", "Runs the test suite", "./run-tests.sh");

// From app.properties (launch.tool.name, launch.tool.description, launch.command)
var agent = new Agent("assistant")
        .withLaunchAppTool();
```

Configure in `app.properties`:

```properties
launch.tool.name=run_tests
launch.tool.description=Runs the test suite
launch.command=./run-tests.sh
```

## Configuration

### Required Properties

Add to `~/.zsmith/app.properties` or any properties file in the loading chain:

```properties
anthropic.api.key=sk-ant-...
anthropic.version=2023-06-01
```

### Example: Anthropic Configuration

A complete `~/.zsmith/app.properties` for the native Anthropic API. `llm.provider` and `claude.model` are shown with their defaults, so both lines are optional; listing them makes the active provider and model explicit:

```properties
anthropic.version=2023-06-01
llm.provider=claude
claude.model=claude-opus-4-8
anthropic.api.key=sk-ant-[]
```

### Model

The default Claude model is `claude-opus-4-8`. Override via system property:

```bash
java -Dmodel=sonnet -cp zbo/zsmith.jar MyAgent.java
```

Partial matching works: `sonnet` resolves to `claude-sonnet-4-7`, `4-7` to `claude-opus-4-7`, etc.

### Forcing the First Tool Call

An agent whose first move is meant to be a question dies on turn one when the model writes the question as prose instead of calling `user_question`: no tool use means `end_turn`, and the loop exits. The opening request therefore carries `"tool_choice": {"type": "any"}` on the Anthropic surface, or `"tool_choice": "required"` on the OpenAI-compatible one. See [forcing tool use](https://docs.claude.com/en/docs/agents-and-tools/tool-use/implement-tool-use) and the [OpenAI chat completions reference](https://platform.openai.com/docs/api-reference/chat/create).

Only the opening turn is forced. Demanding a tool call on every turn would remove the agent loop's sole exit condition and leave it running to `maxIterations`. Requests carrying no tools are untouched, since both APIs reject `tool_choice` on its own.

Switch it off for an endpoint that does not accept the field, or for an agent whose first answer is legitimately prose:

```properties
llm.require.first.tool.call=false
```

### Properties Loading Order

Loaded in order (each layer overrides the previous):

1. `~/.zsmith/app.properties`: global defaults
2. `./app.properties`: local project defaults
3. `~/.zsmith/[agentName]/app.properties`: global agent-specific
4. `./[agentName]/app.properties`: local agent-specific
5. System properties: highest priority

Only keys present in later files override earlier values; other keys are preserved.

### Tool Permissions

Control which tools require user confirmation before execution. Three permission levels: `allow` (execute silently), `deny` (reject), `confirm` (ask user first). Default is `confirm`.

```properties
tools.permissions.default=confirm
tools.permissions.calculator=allow
tools.permissions.current_time=allow
tools.permissions.execute_script=confirm
tools.permissions.read_any_file=confirm
```

Agent-specific permissions in `~/.zsmith/[agentName]/app.properties` override global defaults:

```properties
# A trusted automation agent
tools.permissions.default=allow
tools.permissions.execute_script=confirm
```

### Logging

Agent output is split by role. What the agent **asks and answers** (prompts, the final response, failures, warnings, and the progress bar) always goes to the console: a question the agent blocks on is useless where nobody can read it. Everything else (turns, tool calls, token counts, wire payloads) is *reporting about* the run and can be sent elsewhere:

```properties
# console (default) | file | both
log.sink=file
```

`file` writes to `~/.zsmith/[agentName]/logs/[agentName]-[pid].log`, beside that agent's memories and recordings, named by process so concurrent runs never overwrite one another. `both` keeps the console copy as well. An unopenable destination degrades to the console with a warning rather than failing the run.

Three high-volume channels are off by default and are the usual reason a terminal becomes unreadable during an interactive run:

```properties
# full outgoing API payload
log.request=true
# full raw API response
log.response=true
# the >> / << wire trace
log.llm=true
```

Switching a channel off keeps it off whatever `log.sink` says; where output goes and whether it is produced are separate questions. Every other channel (`agent`, `tool`, `skill`, `memory`, `tokens`, `subagent`, `debug`, `info`) prints unconditionally and follows `log.sink`.

> Combining `log.request`/`log.response` with an agent that uses `user_question` on the console is what makes prompts scroll away mid-conversation. `log.sink=file` is the fix: you keep the payloads without them competing for the terminal.

### System Prompt

Loaded from `system.prompt` files in order (each layer overrides the previous):

1. `~/.zsmith/[agentName]/system.prompt`: global agent-specific
2. `./[agentName]/system.prompt`: local agent-specific
3. `./system.prompt`: highest priority

If no file is found, the constructor parameter is used as fallback.

### Agent Defaults

Every `new Agent(...)` argument falls back to configuration, so an agent constructed with no arguments is fully configurable from a properties file:

```properties
agent.name=zsmith
agent.system.prompt=You are a helpful assistant.
agent.max.iterations=100
agent.temperature=0.1
```

`agent.max.iterations` bounds the chat loop; reaching it ends the run with "Max iterations reached" rather than looping forever.

### Timeouts

Three independent families, all ISO-8601 durations (`PT10S`, `PT5M`, or whatever `Duration.parse` accepts). Each defaults to a value that fails a stuck call rather than hanging a turn:

| Key | Default | Applies to |
|-----|---------|------------|
| `http.connect.timeout` | `PT10S` | every LLM call (Claude, Bedrock, OpenAI) |
| `http.request.timeout` | `PT5M` | every LLM call (generous, since generation is slow) |
| `fetch.connect.timeout` | `PT10S` | the `fetch_url` tool |
| `fetch.request.timeout` | `PT15S` | the `fetch_url` tool |
| `link.connect.timeout` | `PT10S` | the `check_link` tool |
| `link.request.timeout` | `PT10S` | the `check_link` tool |

Tool timeouts are shorter than LLM timeouts on purpose: a slow page should fail its tool call and let the agent continue, not stall the turn.

### Prompt Caching

**On by default.** The system prompt, tool definitions, and conversation prefix are marked with `cache_control`, so repeated turns re-send the same prefix at the cached rate, visible in the `cache_read` counts on the progress bar and in `RunReport`:

```properties
# switch caching off entirely
claude.cache=false

# optional extended time-to-live, passed through to the API as given
claude.cache.ttl=1h
```

Leave `claude.cache.ttl` unset to use the API default. See [prompt caching](https://docs.claude.com/en/docs/build-with-claude/prompt-caching).

### Thinking and Effort

Both are unset by default and are only sent to models whose capability set declares support; configuring them for a model that lacks it is ignored rather than rejected:

```properties
# adaptive thinking, sent only by models that support it
claude.thinking=adaptive

# only honoured when claude.thinking=adaptive
claude.thinking.display=

# effort level, sent only by models that support it
claude.effort=
```

### Sandbox Traversal

Sandboxed file tools skip version-control and build directories, which are the slowest and least informative part of a checked-out repository. The built-in set is `.git`, `.hg`, `.svn`, `target`, `build`, `out`, `bin`, `zbo`, `node_modules`:

```properties
# a comma separated list, REPLACING the built-in set rather than extending it
tools.sandbox.ignore=.git,target,vendor
```

Setting it to an empty value traverses everything.

### Alternative LLM Providers

> First-time users can skip this section: the default `claude` provider works out of the box. Come back when you want Amazon Bedrock, OpenAI, or local inference.

zsmith ships with three clients, selected at runtime via `llm.provider`:

```properties
# Anthropic Messages API (the default)
llm.provider=claude

# Amazon Bedrock Mantle (Anthropic-compatible, reuses the Claude client)
#llm.provider=bedrock

# OpenAI Chat Completions API
#llm.provider=openai

# local GGUF inference via lightmetal.jar, in-process
#llm.provider=lightmetal
```

Agent code is unchanged either way; request and response are translated internally so the Agent loop only ever sees Anthropic-shaped content blocks.

#### Claude endpoint

By default, requests go to `https://api.anthropic.com/v1/messages`. To point at a local Anthropic-compatible endpoint:

```properties
claude.scheme=http
claude.host=localhost
claude.port=8080
```

`claude.port` is optional; omit it to use the scheme default. `claude.scheme` defaults to `https`, `claude.host` to `api.anthropic.com`.

Any Anthropic-compatible gateway can be reached by overriding these optional knobs. All default to the native Anthropic values, so leaving them unset preserves current behavior:

```properties
# request path
claude.path=/v1/messages

# payload model id; default derived from -Dmodel / enum
claude.model=claude-opus-4-8

# name of the auth header carrying anthropic.api.key
anthropic.auth.header=x-api-key

# adds the anthropic-workspace-id header only when set
anthropic.workspace.id=
```

For a `Bearer`-token gateway, set `anthropic.auth.header=Authorization` and put the prefix in the key: `anthropic.api.key=Bearer <token>`.

#### Amazon Bedrock Mantle

[Amazon Bedrock Mantle](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html) exposes an Anthropic-compatible Messages API at its `bedrock-mantle` endpoint. It is selected with `llm.provider=bedrock` and reuses the Claude client. Only the **region**, **model**, and **API key** vary; everything else is convention-derived.

Because the native Anthropic and Bedrock settings never collide, **both can live in the same properties file** and you switch between them by flipping a single line:

```properties
# --- switch provider here ---
# native Anthropic API
llm.provider=claude
# Amazon Bedrock Mantle
#llm.provider=bedrock

# --- native Anthropic ---
anthropic.api.key=sk-ant-...
anthropic.version=2023-06-01

# --- Amazon Bedrock Mantle ---
bedrock.region=eu-north-1
bedrock.api.key=bedrock-api-...

# --- shared ---
# a bare name works for both providers; pick one your Bedrock account can use
claude.model=claude-haiku-4-5
```

When `llm.provider=bedrock`, zsmith derives:

- **endpoint** → `https://bedrock-mantle.<region>.api.aws/anthropic/v1/messages`
- **anthropic-version** → `2023-06-01` (override with `anthropic.version` if needed)
- **API key** → `bedrock.api.key`, falling back to `anthropic.api.key` when unset
- **project header** → `bedrock.project.id`, mapped to whichever header the active wire accepts: `anthropic-workspace-id` on the Messages route, `openai-project` on the Chat Completions route (see below)
- **model prefix** → a **bare** `claude.model` gets the `anthropic.` prefix, so `claude.model=claude-haiku-4-5` resolves to `anthropic.claude-haiku-4-5`

The same bare `claude.model` therefore works under both providers: used as-is for native Anthropic, `anthropic.`-prefixed under Bedrock. An id that already contains a `.` (e.g. `anthropic.claude-haiku-4-5`) is used verbatim. The 529→fallback retry is Anthropic-specific and does not apply to Bedrock model ids.

> **Pick a model your account can use.** Bedrock returns `403 … is not available for this account` for models you have not been granted. List/enable models in the Bedrock console; your account's available Anthropic models determine valid `claude.model` values. See the [Bedrock Mantle docs](https://docs.aws.amazon.com/bedrock/latest/userguide/bedrock-mantle.html) for regions and the [endpoints reference](https://docs.aws.amazon.com/bedrock/latest/userguide/endpoints.html).

##### OpenAI-compatible models (NVIDIA Nemotron)

Bedrock Mantle also serves **non-Anthropic** models, such as [NVIDIA Nemotron Super 3 120B](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-nvidia-nemotron-super-3-120b.html), over its **OpenAI-compatible Chat Completions** route (`/v1/chat/completions`) rather than the Anthropic Messages route. zsmith detects this from the model id and switches wire format automatically, still under `llm.provider=bedrock`, with no extra provider:

```properties
llm.provider=bedrock

# pick a region that offers the model (see model card)
bedrock.region=eu-west-1

bedrock.api.key=bedrock-api-...

# the id carries a '.', so it is used verbatim, with no anthropic. prefix
claude.model=nvidia.nemotron-super-3-120b
```

For such models zsmith derives:

- **endpoint** → `https://bedrock-mantle.<region>.api.aws/v1/chat/completions` (not `/anthropic/v1/messages`)
- **auth** → `Authorization: Bearer <bedrock.api.key>`
- **project header** → `openai-project` (this route **rejects** `anthropic-workspace-id`), sourced from `openai.project` or `bedrock.project.id`
- **request/response** → translated to and from the OpenAI Chat Completions shape, so the Agent loop still sees Anthropic-shaped content blocks and `tool_use`

> **One project id covers both wires.** Set `bedrock.project.id` once and zsmith emits the header the active route accepts (`anthropic-workspace-id` for Anthropic models, `openai-project` for OpenAI-compatible ones like Nemotron), so flipping `claude.model` needs no other change. The wire-native keys `anthropic.workspace.id` / `openai.project` still override it when set. (Setting `anthropic.workspace.id` while running a Nemotron model is what triggers Bedrock's `The anthropic-workspace-id header is not supported for this API format` error; use `bedrock.project.id` instead.)

> **`bedrock.project.id` applies only while `llm.provider=bedrock`.** A global `bedrock.project.id` in `~/.zsmith/app.properties` outlives an agent that switches back with `llm.provider=claude`, so it is deliberately ignored off Bedrock — a Bedrock project id sent to `api.anthropic.com` answers `400 … anthropic-workspace-id header must be a valid workspace ID`. Set `anthropic.workspace.id` when the native API really should carry a workspace.

> **Regions are limited and change over time.** This model is offered only in specific regions, and a region that works today may not be the one in your existing Bedrock config. If you get a "model isn't supported"/availability error, check the current regions on the [model card](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-nvidia-nemotron-super-3-120b.html) and set `bedrock.region` accordingly.

#### OpenAI endpoint

By default, requests go to `https://api.openai.com/v1/chat/completions`. Configurable knobs:

```properties
# every value below is the default; all of them are optional.
# a blank api key omits the Authorization header, which local servers usually want
openai.api.key=sk-...
openai.model=gpt-4o
openai.max.tokens=4096
openai.scheme=https
openai.host=api.openai.com
# unset uses the scheme default port
openai.port=
```

The OpenAI client has no fallback model. Unlike Claude's 529→fallback retry, OpenAI errors propagate directly.

To point at a local Ollama server:

```properties
llm.provider=openai
openai.host=localhost
openai.port=11434
openai.scheme=http
openai.model=llama3.1
```

LM Studio (default port 1234), llama.cpp `--api`, and vLLM expose the same Chat Completions shape and work identically.

#### LightMetal (embedded local inference)

[LightMetal](https://github.com/AdamBien/lightmetal) is a Java 25 GGUF runner that talks to Apple Silicon's Metal via the Foreign Function & Memory API. zsmith reaches it via the `UnaryOperator<String>` SPI (`lm.generation.boundary.LightMetalChat`), so the only compile-time dependency is `java.base`. Drop `lightmetal.jar` on the classpath at runtime and the provider is **auto-selected**, overruling `llm.provider` whatever it is set to. The classpath is the explicit signal; no extra config is needed. The GGUF is loaded once on the first call and reused for every subsequent turn.

```properties
# optional, overrides lightmetal's own config
lightmetal.model=/abs/path/to/model.gguf

# optional, defaults to 4096
lightmetal.max.tokens=4096
```

`lightmetal.model` is **optional** in zsmith. When unset, zsmith omits `model` from the request payload entirely; lightmetal then sources it from its own eager-loaded `~/.lightmetal/app.properties` (or `-Dmodel=...`). So a user who already runs `lmprompt`/`lmserve` against a configured `~/.lightmetal/app.properties` needs zero zsmith-side model config. Set `lightmetal.model` in zsmith only when you want one agent to override the lightmetal-wide default.

LightMetal natively understands Anthropic-shaped `tools` and emits `tool_use` content blocks, so the Agent loop works the same as with Claude. Run agent scripts with `--enable-native-access=ALL-UNNAMED` so the FFM call into `libllama.dylib` is allowed:

```java
#!/usr/bin/java --class-path=zbo/zsmith.jar:lightmetal.jar --enable-native-access=ALL-UNNAMED --source 25
```

For benchmarking or remote inference you can also point the **Claude** client at LightMetal's HTTP server (`-serve` mode); its `/v1/messages` endpoint is byte-compatible with Anthropic's:

```properties
llm.provider=claude
claude.scheme=http
claude.host=localhost
claude.port=8080
```

## Running the Examples

```bash
zb.sh
java -cp zbo/zsmith.jar src/test/java/airhacks/zsmith/MeetingPlannerExample.java
```

```bash
java -cp zbo/zsmith.jar src/test/java/airhacks/zsmith/UserConfirmationExample.java
```

```bash
java -cp zbo/zsmith.jar src/test/java/airhacks/zsmith/EpisodicMemoryExample.java
```

```bash
java -cp zbo/zsmith.jar src/test/java/airhacks/zsmith/SkillsExample.java
```

## Java Script Usage

zsmith agents can run as standalone Java scripts using source-file mode, with no build tool and no compilation step:

```bash
./src/test/java/airhacks/zsmith/userConfirmationExample
```

The script uses a shebang to reference `zbo/zsmith.jar` directly, so build first with `zb.sh`. Example script:

```java
#!/usr/bin/java --class-path=../../../../../zbo/zsmith.jar --source 25

// Requires zbo/zsmith.jar, build first with: zb.sh

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.tools.boundary.Tools;

void main() {

        var agent = new Agent()
                .withSystemPrompt("""
                        You are a helpful assistant with access to tools.
                        Use the user_confirmation tool to ask the user yes/no questions before proceeding with actions.
                        Be concise in your responses.
                        """)
                .withTool(Tools.USER_CONFIRMATION);

        Log.agent("Agent initialized with user_confirmation tool");

        var question = "I want to create a HelloWorld.java example. Can you help?";
        Log.prompt("User: " + question);

        var response = agent.chat(question);
        Log.answer("Agent: " + response);

}
```

No package declaration, no class wrapper: Java 25 implicit classes keep the script minimal. Install system-wide by copying the jar and script to a PATH directory, adjusting the `--class-path` accordingly.

A minimal calculator agent, see [`examples/calculator`](examples/calculator):

```java
#!/usr/bin/java --class-path=../zsmith/zbo/zsmith.jar  --source 25

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.boundary.Tools;

void main() {

var calculator = new Agent("calculator", """
        You are a calculator assistant.
        1. Ask the user for a math expression.
        2. Use the calculator tool to evaluate it.
        3. Show the result to the user.
        4. Loop until the user types 'quit'.
        """)
        .withTools(Tools.USER_QUESTION, Tools.USER_MESSAGE, Tools.CALCULATOR);

calculator.act();
}
```

Run it directly:

```bash
./examples/calculator
```

A file-driven variant ([`examples/fileCalculator`](examples/fileCalculator)) asks the user for input and output paths, reads a math expression from the input file, evaluates it, and writes the numeric result to the output file. Its shebang also lists `lightmetal.jar` on the classpath:

```java
#!/usr/bin/java --class-path=../zsmith/zbo/zsmith.jar:../../lightmetal/zbo/lightmetal.jar --enable-native-access=ALL-UNNAMED --source 25
```

The `../../lightmetal/zbo/lightmetal.jar` entry is **optional**. Drop it (and `--enable-native-access`) to run against Claude. Keep it to auto-select [LightMetal](#lightmetal-embedded-local-inference) for fully on-device inference. No other config change is required, just set `lightmetal.model`.

An inline-tool variant ([`examples/currentDate`](examples/currentDate)) defines its tools directly in the script via `Tool.of(...)` instead of pulling them from the `Tools` enum:

```java
#!/usr/bin/java --class-path=../zsmith/zbo/zsmith.jar  --source 25

import java.time.LocalDate;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.boundary.Tool;

void main() {

var currentDate = Tool.of(
        "current_date",
        "Returns the current date in ISO format (yyyy-MM-dd).",
        _ -> LocalDate.now().toString());

var printMessage = Tool.of(
        "print_user_message",
        "Prints a message to the user's console.",
        Tool.schema(Tool.Prop.string(PrintField.message, "The message to print to the user")),
        input -> {
            IO.println(input.getString("message"));
            return "Message printed to user";
        });

var agent = new Agent("current-date", """
1. Use the current_date tool to obtain today's date.
2. Use the print_user_message tool to print the date to the user.
""")
.withTools(currentDate, printMessage);

agent.act();
}

enum PrintField { message }
```

Run it directly:

```bash
./examples/currentDate
```

`Tool.of(...)` is the inline counterpart to implementing the `Tool` interface, useful when a tool is small enough to live next to the agent that uses it. Two overloads are available:

- `Tool.of(name, description, schema, fn)`: full form with an explicit input schema (use `Tool.schema(...)` to declare parameters).
- `Tool.of(name, description, fn)`: short form for parameter-less tools; the input schema defaults to `Tool.emptySchema()`.

### JFR Configuration

zsmith emits JDK Flight Recorder events for every agent turn, LLM API call, tool invocation, sub-agent dispatch, skill load, and memory access, all under the `zsmith` category.

The simplest way to record them is configuration: no launcher flag, and it travels with the agent when the script is copied:

```properties
jfr.enabled=true
```

The recording starts when an agent begins its first `chat`/`act` and is written to `~/.zsmith/[agentName]/recordings/[agentName]-[pid].jfr` when the JVM exits. Off by default: a recording is the whole run on disk. If the JVM is *already* recording, because you passed the flag below, zsmith leaves that recording alone rather than starting a second one.

Alternatively, add `-XX:StartFlightRecording` to the shebang. This starts earlier, so it also captures JVM startup:

```java
#!/usr/bin/java -XX:StartFlightRecording=filename=calculator.jfr,dumponexit=true,settings=profile --class-path=../zsmith/zbo/zsmith.jar --source 25
```

Open `calculator.jfr` in JDK Mission Control and filter the event browser by category `zsmith` to see:

| Event | Category | What it captures |
|-------|----------|------------------|
| `airhacks.zsmith.agent.Turn` | `zsmith / agent` | One iteration of the chat loop with stop reason and tool counts |
| `airhacks.zsmith.claude.APICall` | `zsmith / claude` | HTTP call to the Anthropic Messages API with token usage and attempt number |
| `airhacks.zsmith.openai.APICall` | `zsmith / openai` | HTTP call to the OpenAI Chat Completions API with token usage |
| `airhacks.zsmith.lightmetal.APICall` | `zsmith / lightmetal` | In-process GGUF generation call with token usage |
| `airhacks.zsmith.tools.Invocation` | `zsmith / tools` | Single tool execution with outcome, result size, and failure type |
| `airhacks.zsmith.subagent.Dispatch` | `zsmith / subagent` | Delegation to a sub-agent |
| `airhacks.zsmith.skills.Load` | `zsmith / skills` | Skill read from disk during `SkillStore` init |
| `airhacks.zsmith.memory.Access` | `zsmith / memory` | Read or write of a persistent memory store |

For a focused recording, pass a custom `.jfc` file enabling only the `airhacks.zsmith.*` events via `settings=zsmith.jfc`.

### Correlation

Every event of one `chat` or `act` invocation carries the same `runId`, so the recording can be grouped instead of guessed at. A tool call issued in parallel runs on its own virtual thread, which makes timestamps and thread ids useless for the job. `Turn` events also carry `iteration`, `depth`, and `parentRunId`, so a delegated run points back at the run that delegated to it and the sub-agent tree can be reconstructed. `Invocation` events carry the model's own `toolUseId`, which ties the event to the exact content block that requested it.

Filter by `runId` in JDK Mission Control to see one conversation end to end, or read it in code:

```java
var reports = EventLog.replay(Path.of("calculator.jfr"));
reports.values().stream()
        .filter(RunReport::incomplete)
        .forEach(report -> IO.println(report.summary()));
```

`EventLog.replay(Path)` reads a finished recording whole, which is what you want for anything whose number gets compared against another run's. `EventLog.live()` consumes this JVM's events as they are flushed. Start it *before* constructing the agent it should observe, since events committed before the stream is running are never delivered, and a flush interval sits between a commit and the stream seeing it.

The events deliberately carry no content: no prompts, no tool inputs, no error messages. JFR interns strings per chunk, so verbatim payloads would neither deduplicate nor stay small, and a `.jfr` is an artifact people hand around with no redaction stage. What the stream gives you is which run to look at; what happened in it lives in the transcript.

### Diagnosing a Run

`EventLog.replay` says what a run cost. `RunDiagnostics` says whether that was avoidable:

```java
RunDiagnostics.diagnose(Path.of("airhacksfm-93248.jfr"))
        .forEach(finding -> IO.println(finding.line()));
```

Every rule is arithmetic over recorded fields — no model reads the recording. The findings:

| Finding | What it means |
|---------|---------------|
| `cache-expired` | A call read nothing from cache and re-created a significant prefix at write price. A run's *first* call never counts: it reads nothing from cache by definition, which is what separates a real expiry from a sub-agent starting cold. |
| `idle-gap` | The run's own calls were spaced further apart than the prompt cache lives — the cause of which `cache-expired` is the effect. Names the tool that filled the stretch, where one did: a question nobody answered and a sub-agent still working are the same gap and not the same problem. |
| `context-carried` | What each tool left in the conversation, summed per tool and ranked by bytes times the turns that carried them. Judged on the carry, not on any one result: the largest single result is routinely not the largest cost. |
| `batching` | How many tool calls the run got out of each turn that asked for any. Counted per turn, never by how the tools executed — concurrency is a property `Tool.parallel()` declares, so a run using only sequential tools has serialized nothing. |
| `retries`, `tool-failures` | Straight from the run's report, keyed by what went wrong. |
| `incomplete` | The run never reached a terminal turn. |
| `subagent-cost` | What a delegated run spent, reported against the child and never added to the parent. |

Findings are graded `WARNING`, `NOTE` or `PASS`. A healthy run reports its passes rather than
staying silent — an empty report cannot be told apart from an unchecked one. Each finding carries
the measurements its verdict rests on, so it can be argued with rather than believed.

[`runAnalyzer`](runAnalyzer), at the repository root beside `zsinstall`, prints the whole thing for a `.jfr`:

```sh
./runAnalyzer ~/.zsmith/airhacksfm/recordings/airhacksfm-93248.jfr
```

```
run d595382d-12cf-464a-8ddf-0b5fc98ec35f
  WARNING cache-expired          the prefix was re-created at write price instead of read from cache — turn 6 read 0 cached tokens and wrote 68789 after 16m 23s waiting on user_question
  NOTE    idle-gap               the run was blocked longer than the prompt cache lives — 16m 23s waiting on user_question between turn 5 and turn 6, cache TTL is 5m 00s
  NOTE    context-carried        recall_memory carried 1348 KB through the run — 3 calls totalling 81211 bytes, longest carried 17 turns
  PASS    retries                every API call succeeded on its first attempt — 18 calls, 0 retries
```

What a run *should* have done in any sense a threshold cannot express stays out: that needs the
conversation, which lives in the transcript stored under the same `runId`.

### Transcripts

Set `transcripts.enabled=true` to store each conversation under its `runId` in the agent's own database, next to its memories and improvement backlog:

```properties
# ~/.zsmith/app.properties
transcripts.enabled=true
```

Off by default: this writes whole conversations to disk. Records are browsable XHTML pages under `~/.zsmith/[agentName]/memory/transcripts/`, and readable in code through `TranscriptLog.forAgent(name).read(runId)`.

## Benchmarks

The [`benchmarks/`](benchmarks/) directory holds executable agent benchmarks that score tool-calling behavior against seeded ground truth (no LLM judge) along orthogonal axes whose results can disagree. [`agentLoopBenchmark`](benchmarks/agentLoopBenchmark) drives an agent through a *pointer-chasing* chain (serial loop-following); [`agentParallelismBenchmark`](benchmarks/agentParallelismBenchmark) gives the agent independent lookups and measures whether it *batches* them into one turn or serializes them, the inverse axis; [`agentErrorRecoveryBenchmark`](benchmarks/agentErrorRecoveryBenchmark) injects transient tool failures into the chain and measures whether the agent *retries* or gives up, the robustness axis. Every run prints one normalized markdown table row (`Benchmark | Model | Size | Calls | Turns | Result`) that pastes directly into the results table in [`benchmarks/README.md`](benchmarks/README.md); see there for mechanisms and sweep usage.

## Skills

Skills are reusable prompt snippets stored as `SKILL.md` files. Each skill uses frontmatter for metadata:

```markdown
---
name: explain
description: Explains a concept using an analogy and a short example
---
When explaining a concept:

1. Start with a one-sentence definition
2. Give an everyday analogy
3. Show a minimal code example (if applicable)
4. End with one common misconception

Keep it under 10 sentences total.
```

Default skill resolution (each layer overrides the previous):

1. `~/.zsmith/skills/`: global skills
2. `~/.zsmith/[agentName]/skills/`: global agent-specific
3. `./skills/`: local project skills
4. `./[agentName]/skills/`: local agent-specific

Additional directories join the chain (lowest precedence, before layer 1) via the `skills.directories` configuration property: a comma-separated list where `~` expands to the user home. E.g. to reuse Claude Code skills:

```properties
# ~/.zsmith/app.properties
skills.directories=~/.claude/skills
```

```java
var agent = new Agent()
        .withSkills();
```

Custom skill directory:

```java
var agent = new Agent()
        .withSkills("path/to/skills");
```

Preselected skills: load only the named skills from the default resolution chain:

```java
var agent = new Agent("planner")
        .withSkillsNamed("explain", "summarize");
```

Skills not matching the given names are excluded from the catalog and from `load_skill`.

Eager skills: inline the full skill content into the system prompt at construction time instead of exposing the `load_skill` tool. Use this when a skill is not optional context but the agent's job description (e.g. review rules the model must always apply). Names resolve through the same chain as `withSkillsNamed`, including `skills.directories`; `withEagerSkills(SkillStore store)` accepts a custom store:

```java
var agent = new Agent("reviewer", "You review Java code.")
        .withEagerSkills("java-conventions");
```

## Episodic Memory

Agents store and recall information across conversations using `EpisodicMemoryStore`. Memories are classified by type (`user`, `feedback`, `project`, `reference`), and each one is persisted as its own XHTML page, so the memory folder is a browsable website: open `index.html` to read what an agent remembers, delete a page to make it forget.

```
~/.zsmith/planner/memory/
├── index.html                        # links the tables
└── episodes/
    ├── index.html                    # links the memories, oldest first
    ├── 2026-08-08-132706.html        # content, timestamp, type
    └── 2026-08-08-141902.html
```

Agent memory: the type decides the scope. What the agent learns about the *user* is written to the shared `~/.zsmith/memory`, because it is true whatever the task; `project`, `reference` and `feedback` stay in `~/.zsmith/[agentName]/memory`. Both scopes are read as one, so a second agent, or a subagent, knows the same person without inheriting the first agent's notes:

```java
var agent = new Agent("planner")
        .withEpisodicMemory();
```

Single scope: every memory, whatever its type, in the shared database:

```java
var agent = new Agent("planner")
        .withSharedEpisodicMemory();
```

Single scope at a custom location:

```java
var agent = new Agent()
        .withEpisodicMemory(new EpisodicMemoryStore(Path.of("custom-memory")));
```

Only `user` memories cross agents. An agent writing `project` notes straight to the shared database with `withSharedEpisodicMemory()` keeps them to itself.

Storing a memory the store already holds (same text, same type) is skipped, so a model repeating itself across turns does not crowd out the injection caps (`zsmith.memory.injected.per_type`, `zsmith.memory.injected.max_total`, applied across both scopes together).

Writes are atomic and per memory, so an interrupted write costs at most the memory being written, and two agents sharing a folder do not overwrite each other. An `episodic-memory.json` from an earlier release is imported on first start and moved aside as `episodic-memory.json.migrated`; repeated statements collapse into one and user memories move to the shared scope on the way in.

## Improvement Log

Opt-in. Adds a `report_improvement` tool with which the agent records where its own instructions fell short (the system prompt, a skill, or a tool description) as an `improvements` table next to its memories:

```java
var agent = new Agent("planner")
        .withEpisodicMemory()
        .withImprovementLog();
```

```
~/.zsmith/planner/memory/
├── index.html          # links both tables
├── episodes/
└── improvements/       # artifact, name, observation, trigger, suggestion
```

A report records an incident rather than proposing a change. `observation` (what the instruction failed to say) and `trigger` (the input that exposed it) are required; `suggestion` is optional, because an agent never observes how a different instruction would have played out; it is a reliable witness and an unreliable designer of its own prompt. A report missing its trigger is refused, which is what keeps the table from filling with "the task went well". Reporting the same gap twice is skipped.

Nothing written here reaches the agent. The log is read and applied by a human: an agent that edits its own `system.prompt` has no oversight, and a bad edit changes the behaviour that would justify the next one.

Keep it off for agents in steady use: a tool definition ships with every request, and one that fires rarely still costs its description on every turn while diluting selection across the others.

## Subagents

Agents can delegate tasks to other agents via `withSubAgent()`. The child agent becomes a callable tool (`delegate_to_<name>`).

By default, multiple `withSubAgent()` invocations run in parallel, but the **first successful run of each subagent is forced sequential** so that any `confirm`-level tool permission prompts appear cleanly one at a time on stdout/stdin instead of colliding across virtual threads. Once a subagent has completed once, a marker is written to `~/.zsmith/<subAgentName>/.first_run_completed` and subsequent runs fan out in parallel. Use `withSequentialSubAgent()` to opt out of parallelism entirely; delete the marker file to force another sequential warm-up.

Podcast transcription example: the coordinator asks for the transcript path, reads the file, delegates link verification, stores guests and links in memory, and copies the result to the clipboard:

```java
var linkChecker = new Agent("link_checker", """
        You verify URLs. For each URL given, use the check_link tool
        to confirm it is reachable. Return a markdown status list.
        """)
        .withTool(Tools.LINK_CHECKER);

var transcriber = new Agent("transcriber", """
        You process podcast transcriptions.
        1. Ask the user for the transcript file path.
        2. Read the transcript.
        3. Extract all guest names and URLs mentioned.
        4. Delegate link verification to the link_checker agent.
        5. Store guests and verified links in memory.
        6. Write a summary with link status annotations to the clipboard.
        """)
        .withTools(Tools.USER_QUESTION, Tools.READ_ANY_FILE, Tools.WRITE_CLIPBOARD)
        .withSubAgent(linkChecker)
        .withEpisodicMemory();

var response = transcriber.act();
```

As a standalone Java script with shebang:

```java
#!/usr/bin/java --class-path=zbo/zsmith.jar --source 25

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.boundary.Tools;

void main() {

        var linkChecker = new Agent("link_checker", """
                You verify URLs. For each URL given, use the check_link tool
                to confirm it is reachable. Return a markdown status list.
                """)
                .withTool(Tools.LINK_CHECKER);

        var transcriber = new Agent("transcriber", """
                You process podcast transcriptions.
                1. Ask the user for the transcript file path.
                2. Read the transcript.
                3. Extract all guest names and URLs mentioned.
                4. Delegate link verification to the link_checker agent.
                5. Store guests and verified links in memory.
                6. Write a summary with link status annotations to the clipboard.
                """)
                .withTools(Tools.USER_QUESTION, Tools.READ_ANY_FILE, Tools.WRITE_CLIPBOARD)
                .withSubAgent(linkChecker)
                .withEpisodicMemory();

        transcriber.act();
}
```

Custom tool name, description, and max delegation depth:

```java
var agent = new Agent("coordinator")
        .withTool(new SubAgentTool(linkChecker, "verify_links",
                "Verifies all URLs in the given text", 2));
```

## Built-in Tools

| Tool | Name | Description |
|------|------|-------------|
| `CalculatorTool` | `calculator` | Performs basic arithmetic operations: add, subtract, multiply, divide |
| `CurrentTimeTool` | `current_time` | Returns the current date and time |
| `ReadClipboardTool` | `read_clipboard` | Reads text content from the system clipboard |
| `WriteClipboardTool` | `write_clipboard` | Writes text content to the system clipboard |
| `ReadFileTool` | `read_file` | Reads the contents of a file within the sandbox directory |
| `WriteFileTool` | `write_file` | Writes content to a file within the sandbox directory |
| `ListFilesTool` | `list_files` | Lists all files within the sandbox directory |
| `ListFilesEndingTool` | `list_files_ending` | Lists all files within the sandbox directory whose names end with a given suffix |
| `SearchFilesTool` | `search_files` | Searches sandbox file contents for a regular expression; returns `path:line: text` matches like `grep -n`, optionally filtered by file suffix |
| `EditFileTool` | `edit_file` | Replaces an exact, verbatim occurrence of one string with another in a sandboxed file |
| `FindFilesTool` | `find_files` | Lists sandboxed files whose name or relative path matches a glob pattern |
| `ReadAnyFileTool` | `read_any_file` | Reads a file from any location on the filesystem (**not sandboxed**) |
| `WriteAnyFileTool` | `write_any_file` | Writes a file at any absolute path, overwriting or appending (**not sandboxed**) |
| `LinkCheckerTool` | `check_link` | Verifies a URL is reachable; returns status code, final URL after redirects, and content type |
| `FetchUrlTool` | `fetch_url` | Fetches a URL with a browser User-Agent and returns status, content type, and up to 20000 chars of the body |
| `UserConfirmationTool` | `user_confirmation` | Asks the user a yes/no question and returns the answer |
| `UserQuestionTool` | `user_question` | Asks the user a question and returns the typed answer |
| `UserMessageTool` | `user_message` | Presents a message to the user |
| `StoreMemoryTool` | `store_memory` | Stores an episode in long-term memory for future recall |
| `RecallMemoryTool` | `recall_memory` | Recalls past memories, optionally filtered by type or limited to recent entries |
| `LoadSkillTool` | `load_skill` | Loads a skill by name (added automatically with `withSkills()`) |
| `ExecuteScriptTool` | `execute_script` | Executes a script and returns its output |
| `LaunchAppTool` | *(config-driven)* | Launches an external application with arguments (name, description, command from config or constructor) |

## Custom Tools

Implement the `Tool` interface. Use `Tool.Prop` with an enum for type-safe field names and `Tool.schema()` to define the input schema:

```java
public class MyTool implements Tool {

    enum Field { param, count }

    public String toolName() {
        return "my_tool";
    }

    public String description() {
        return "Does something useful";
    }

    public String inputSchema() {
        return Tool.schema(
            Prop.string(Field.param, "Parameter description"),
            Prop.integer(Field.count, "How many times").optional()
        );
    }

    public String execute(JSONObject input) {
        return "Result: " + input.getString(Field.param.name());
    }
}
```

Available `Prop` types: `string`, `stringEnum` (with allowed values), `number`, `integer`. Any prop can be marked `.optional()`.

---

architecture by [bce.design](https://bce.design) | built by [zb](https://github.com/AdamBien/zb) | tested by [zunit](https://github.com/AdamBien/zunit) | skill provided by: [airails](https://airails.dev) | powered by [airhacks.live](https://airhacks.live)
