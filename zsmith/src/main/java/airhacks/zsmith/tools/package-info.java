/// # Tools
/// > Offer an agent a governed catalog of executable capabilities — file access, computation, web, clipboard, user interaction and process execution — and run them on the LLM's behalf.
///
/// ## Boundary
/// <!-- composition -->
/// - `offer-tool-catalog` — expose every ready-to-use handler as a named catalog
/// - `offer-sandbox-tools` — expose file handlers bound to a caller-supplied sandbox root
/// - `offer-tool-profile` — expose a curated grouping of handlers for one capability
/// - `register-record-tool` — adopt an annotated record as a handler
/// - `publish-tool-contract` — publish the handler contract for tools implemented outside the framework
/// - `describe-tools` — publish each handler's name, description and input schema
/// - `resolve-tool-permission` — report the permission configured for a handler
///
/// <!-- file access, confined to a sandbox root -->
/// - `read-file` — return a sandboxed file's contents, optionally a numbered line range
/// - `write-file` — store content at a sandboxed path
/// - `edit-file` — replace an exact text occurrence in a sandboxed file
/// - `list-files` — enumerate the sandboxed files
/// - `list-files-ending` — enumerate the sandboxed files whose name carries a suffix
/// - `search-files` — return the sandboxed lines matching a regular expression
///
/// <!-- file access, unconfined -->
/// - `read-any-file` — return the contents of a file at any absolute path
/// - `write-any-file` — store content at any absolute path
///
/// <!-- computation and time -->
/// - `calculate` — evaluate one basic arithmetic operation
/// - `report-current-time` — return the current date and time
///
/// <!-- web -->
/// - `fetch-url` — return a URL's body
/// - `check-link` — report a URL's reachability
///
/// <!-- clipboard -->
/// - `read-clipboard` — return the system clipboard's text
/// - `write-clipboard` — store text on the system clipboard
///
/// <!-- user interaction -->
/// - `message-user` — present a message
/// - `ask-user` — pose a question and return the typed answer
/// - `confirm-with-user` — pose a yes/no question and return the answer
///
/// <!-- process execution -->
/// - `execute-script` — run an executable script and return its output
/// - `launch-app` — run a preconfigured command with arguments and return its output
///
/// ## Requirements
///
/// ### R1: Compose a tool set
/// - R1.1 — The BC shall expose every ready-to-use handler as a catalog addressable by name.
/// - R1.2 — When a sandbox root and a selection of file handlers are supplied, the BC shall bind exactly the selected handlers to that root.
/// - R1.3 — When a sandbox root is supplied without a selection, the BC shall bind every sandboxed file handler to that root.
/// - R1.4 — If a sandboxed tool set is requested, then the BC shall withhold every unconfined file handler from it. _(why: a caller asking for a sandbox is asking for confinement; silently including read-any-file would void it)_
/// - R1.5 — The BC shall expose curated handler groupings for user interaction, clipboard and file access.
/// - R1.6 — The BC shall publish the handler contract at its boundary so a tool implemented outside the framework is interchangeable with every built-in handler. _(why: the framework is published to Maven; without a boundary contract, plugin authors depend on a control-layer type)_
///
/// ### R2: Publish what a caller needs to govern a tool
/// <!-- this BC describes and classifies tools; enforcing the classification during a turn is the agent BC's R3 -->
/// - R2.1 — The BC shall publish each handler's name, description and input schema.
/// - R2.2 — The BC shall report each handler's configured permission, and shall report confirmation as required when none is configured. _(why: an unconfigured tool must never run unattended, so the safe value is the absent one)_
/// - R2.3 — The BC shall report whether a handler may run concurrently with others.
/// - R2.4 — When a record tool is registered, the BC shall derive its name from the class name in snake case.
///
/// ### R3: Confine file access to the sandbox root
/// - R3.1 — When a sandboxed operation receives a relative path below the sandbox root, the BC shall resolve it against that root.
/// - R3.2 — If a sandboxed path is absolute, then the BC shall reject the operation.
/// - R3.3 — If a sandboxed path contains a parent-directory segment, then the BC shall reject the operation.
/// - R3.4 — If a sandboxed path is empty or carries a null character, then the BC shall reject the operation.
/// - R3.5 — If a sandboxed path resolves through a symbolic link leading outside the sandbox root, then the BC shall reject the operation. _(why: a symlink is the one way a syntactically valid relative path still escapes the root)_
///
/// ### R4: Read a sandboxed file
/// - R4.1 — When a sandboxed file is requested without a line range, the BC shall return its full contents unnumbered. _(why: existing agent prompts parse raw source, so numbering by default would silently change every one)_
/// - R4.2 — Where line numbering is requested, the BC shall prefix each returned line with its absolute line number.
/// - R4.3 — When a line range is requested, the BC shall return only the lines it covers.
/// - R4.4 — When a line range is returned, the BC shall report the file's total line count. _(why: a reader slicing a large file cannot otherwise tell whether more remains)_
/// - R4.5 — If a line range extends past the last line, then the BC shall return the lines up to the last line.
/// - R4.6 — If a line range starts past the last line, then the BC shall report that the range lies beyond the file.
/// - R4.7 — If the requested file is absent, then the BC shall report that it was not found.
///
/// ### R5: Write a sandboxed file
/// - R5.1 — When content is stored at a sandboxed path, the BC shall create any missing parent directories.
/// - R5.2 — Where appending is requested, the BC shall preserve the existing content and add to it.
/// - R5.3 — Where appending is not requested, the BC shall replace the existing content.
///
/// ### R6: Enumerate sandboxed files
/// - R6.1 — The BC shall return every traversable file below the sandbox root as a root-relative path.
/// - R6.2 — Where a name suffix is supplied, the BC shall return only the files whose name carries it.
/// - R6.3 — The BC shall return the paths in a stable order. _(why: an unstable listing makes a reviewer's per-file fan-out unreproducible)_
/// - R6.4 — If no file matches, then the BC shall report that none was found.
///
/// ### R7: Search sandboxed file contents
/// - R7.1 — When a regular expression is supplied, the BC shall return each matching line with its root-relative path and line number.
/// - R7.2 — Where a name suffix is supplied, the BC shall search only the files whose name carries it.
/// - R7.3 — If the expression is malformed, then the BC shall report it as invalid rather than fail.
/// - R7.4 — If the matches exceed the reportable limit, then the BC shall return the limit and state that the result was truncated.
/// - R7.5 — If a file cannot be read as text, then the BC shall skip it and continue the search.
///
/// ### R8: Exclude generated and version-control noise from traversal
/// - R8.1 — The BC shall exclude a built-in set of build and version-control directories from every sandboxed traversal. _(why: searching a checked-out repository spent the reader's context on version-control object files)_
/// - R8.2 — Where an ignore set is configured, the BC shall exclude it in place of the built-in set.
/// - R8.3 — When a directory is excluded, the BC shall exclude its whole subtree.
/// - R8.4 — If the configured ignore set is empty, then the BC shall traverse every directory below the sandbox root. _(why: an explicit empty set is the only way to opt out of exclusion)_
///
/// ### R9: Access files outside the sandbox
/// - R9.1 — When an absolute path is supplied, the BC shall return that file's contents.
/// - R9.2 — When content and an absolute path are supplied, the BC shall store the content there.
/// - R9.3 — If the requested file is absent, then the BC shall report that it was not found.
///
/// ### R10: Evaluate arithmetic
/// - R10.1 — When an operation and two operands are supplied, the BC shall return the result.
/// - R10.2 — If a division by zero is requested, then the BC shall report it as an error.
/// - R10.3 — If the operation is unrecognised, then the BC shall report it as unsupported.
///
/// ### R11: Report the current time
/// - R11.1 — The BC shall return the current date and time.
///
/// ### R12: Fetch a URL
/// - R12.1 — When a URL is supplied, the BC shall return its status, content type and body.
/// - R12.2 — The BC shall truncate a body exceeding the reportable length.
/// - R12.3 — If the URL is unreachable, then the BC shall report the failure rather than fail.
///
/// ### R13: Check a link
/// - R13.1 — When a URL is supplied, the BC shall report its status code, its final URL after redirects and its content type.
/// - R13.2 — If the URL is unreachable, then the BC shall report it as unreachable.
///
/// ### R14: Exchange text with the clipboard
/// - R14.1 — The BC shall return the system clipboard's current text.
/// - R14.2 — When text is supplied, the BC shall store it on the system clipboard.
/// - R14.3 — If the clipboard holds no text, then the BC shall report it as empty.
///
/// ### R15: Interact with the user
/// - R15.1 — When a message is supplied, the BC shall present it and confirm it was shown.
/// - R15.2 — When a question is supplied, the BC shall return the user's typed answer.
/// - R15.3 — When a yes/no question is supplied, the BC shall return the user's decision.
///
/// ### R16: Execute a process
/// - R16.1 — When an executable script is named, the BC shall run it and return its combined output.
/// - R16.2 — If the named script is absent or not executable, then the BC shall refuse to run it.
/// - R16.3 — If a process exits non-zero, then the BC shall return its output together with the exit code.
/// - R16.4 — If a process outlives its timeout, then the BC shall terminate it and report the timeout. _(why: an unbounded child process hangs the agent loop with no diagnostic)_
/// - R16.5 — Where a launch command is configured, the BC shall expose it as a named handler taking arguments.
///
/// ### R17: Edit a sandboxed file
/// - R17.1 — When a target text, its replacement and a sandboxed path are supplied and the target occurs exactly once in the file, the BC shall replace that occurrence and leave the rest of the file unchanged. _(why: a full rewrite regenerates untouched content, where silent corruption happens; an anchored edit cannot)_
/// - R17.2 — Where replacing every occurrence is requested, the BC shall replace all occurrences and report their count.
/// - R17.3 — If the target text is absent from the file, then the BC shall reject the edit and report that the target was not found.
/// - R17.4 — If the target text occurs more than once and replacing every occurrence is not requested, then the BC shall reject the edit and report the occurrence count. _(why: an ambiguous target could land the edit on the wrong occurrence silently; the count tells the caller how much anchoring context to add)_
/// - R17.5 — If the target text is empty or equals its replacement, then the BC shall reject the edit. _(why: an empty target is a write in disguise; an identical replacement signals a confused caller)_
/// - R17.6 — If the requested file is absent, then the BC shall report that it was not found.
///
/// ## Entities
/// - Tool, ToolUse, ToolResult, ToolPermission
///
/// ## Decisions
/// - D1 — Line numbering on `read-file` is opt-in, defaulting to off. _(why: `javaConventionsReviewer` and every other existing prompt parse raw source; rejected: numbering always, which changes output for all current agents, and numbering only when sliced, which leaves whole-file findings uncitable)_
/// - D2 — Traversal exclusion ships as a built-in directory set overridable through configuration. _(why: correct with zero setup and escapable when wrong; rejected: honouring `.gitignore`, whose glob semantics are a parser this project will not carry, and a per-call exclude parameter, which leaves the common case wasteful whenever the model omits it)_
/// - D3 — Review scope stays whole-tree; the BC offers no diff-aware traversal. _(why: keeps the file handlers free of version-control coupling; rejected: a diff tool scoping review to changed files)_
/// - D4 — Edit matching is exact and verbatim. _(why: a fuzzy match that lands wrong edits silently, while an exact miss is loud and costs one retry with a fresh read; rejected: whitespace-tolerant matching)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Read-before-edit enforcement — would require per-session file state; the unique-match rule carries the safety
/// - Enforcing a tool's permission, executing a requested tool, and turning a failure into an error result — declared and owned by the agent BC's R3
/// - File metadata (size, line count) as a standalone operation — triage before reading
/// - Paging beyond the search match limit
/// - Diff-aware traversal, and any other version-control awareness
/// - Running an arbitrary command line; only a named script or a preconfigured command runs
package airhacks.zsmith.tools;
