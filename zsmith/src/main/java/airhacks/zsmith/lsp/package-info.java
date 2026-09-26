/// # Lsp
/// > Navigate a code base through a language server — find symbols, definitions, references, implementations, callers and callees — and offer that navigation to an agent as tools.
///
/// ## Boundary
/// <!-- composition -->
/// - `offer-lsp-tools` — expose every navigation operation as a handler bound to a workspace root
/// - `release-server` — end the workspace's language server in an orderly way
///
/// <!-- navigation, every location reported root-relative -->
/// - `find-symbols` — return the locations of the workspace symbols matching a name query
/// - `find-definition` — return where the symbol at a position is defined
/// - `find-references` — return every place the symbol at a position is used
/// - `find-implementations` — return every concrete implementation of the symbol at a position
/// - `find-callers` — return every call into the callable symbol at a position
/// - `find-callees` — return every call the callable symbol at a position makes
///
/// ## Requirements
///
/// ### R1: Offer navigation as agent tools
/// - R1.1 — When a workspace root is supplied, the BC shall expose every navigation operation as a named handler fulfilling the published tool contract. _(why: an agent equips code navigation the same way it equips file access)_
/// - R1.2 — The BC shall report each location as its root-relative path, one-based line, one-based column and the text of that source line. _(why: agents already parse the path:line form of search-files; the line text spares a follow-up read)_
/// - R1.3 — The BC shall return locations in a stable order. _(why: a language server's order is unspecified; an unstable answer makes a per-hit fan-out unreproducible)_
/// - R1.4 — If the locations exceed the reportable limit, then the BC shall return the limit and state that the result was truncated.
/// - R1.5 — If no location is found, then the BC shall report that none was found.
/// - R1.6 — If the server names a location outside the workspace root, then the BC shall report it as the server names it, without line text. _(why: a definition in a library is a valid answer, but it is not a workspace file to read)_
///
/// ### R2: Run the language server
/// - R2.1 — When the first navigation request of a workspace arrives, the BC shall start the language server from the configured launch command and reuse that server for every later request. _(why: a server indexes on start; starting one per request would make every answer slow and stale)_
/// - R2.2 — If no launch command is configured, then the BC shall report navigation as unavailable rather than fail.
/// - R2.3 — When a server is started, the BC shall complete the protocol's initialization handshake, rooted at the workspace root, before sending it any navigation request.
/// - R2.4 — When the server initiates a request, the BC shall answer it, and when the server sends a notification, the BC shall accept it, so a server-initiated message never stalls a navigation request. _(why: real servers register capabilities, ask for configuration and report progress while indexing; an unanswered request hangs them)_
/// - R2.5 — If the server does not provide a requested operation, then the BC shall report that operation as unsupported by the server.
/// - R2.6 — If the server cannot be started, then the BC shall report the cause in the request's result.
/// - R2.7 — If the server exits or its connection breaks during a request, then the BC shall report the failure in that request's result and start a fresh server on the next request. _(why: a crashed server must cost one failed call, never the rest of the session)_
/// - R2.8 — If a request outlives its timeout, then the BC shall report the timeout in its result, discard the server and start a fresh one on the next request.
/// - R2.9 — When the server is released, the BC shall ask it to shut down and then to exit, so no orphaned server process remains.
///
/// ### R3: Find symbols by name
/// - R3.1 — When a name query is supplied, the BC shall return the location of every workspace symbol the server matches to it.
/// - R3.2 — If the query is empty, then the BC shall reject the request. _(why: an empty query asks some servers for every symbol in the workspace)_
///
/// ### R4: Resolve a position
/// - R4.1 — When a root-relative path, a one-based line and a one-based column are supplied, the BC shall query the server at that position of that file against the file's current on-disk content. _(why: the server may not have seen the file yet, and the agent may have just edited it)_
/// - R4.2 — If the path is absolute, escapes the workspace root or names an absent file, then the BC shall reject the request.
/// - R4.3 — If the line lies past the last line of the file, then the BC shall reject the request and report the file's line count.
/// - R4.4 — If the line or the column is below one, then the BC shall reject the request.
///
/// ### R5: Find the definition
/// - R5.1 — When a position is supplied, the BC shall return every location where the symbol at it is defined.
///
/// ### R6: Find references
/// - R6.1 — When a position is supplied, the BC shall return every location where the symbol at it is used, including its declaration.
///
/// ### R7: Find implementations
/// - R7.1 — When a position is supplied, the BC shall return every location implementing the symbol at it.
///
/// ### R8: Find callers and callees
/// - R8.1 — When a position on a callable symbol is supplied, the BC shall return the location of every call into it.
/// - R8.2 — When a position on a callable symbol is supplied, the BC shall return the location of every call it makes.
/// - R8.3 — If the position names no callable symbol, then the BC shall report that nothing callable is there.
///
/// ## Entities
/// - Location, Position, ServerCapabilities
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Hover, document outline and completion — not requested; may be declared later as further operations
/// - Anything that changes code through the server: rename, code actions, formatting
/// - Publishing the server's diagnostics
/// - Routing a mixed code base to several servers, one per language — one configured server per workspace (system doc D2)
/// - Bundling, installing or discovering a language server; the launch command is configured
/// - Text search over file contents — the tools BC's `search-files`
/// - Keeping the server in sync with edits beyond the file of the current request
package airhacks.zsmith.lsp;
