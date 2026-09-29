/// # Htmldb
/// > Key-value persistence in which the storage format is also the UI: every record is a browsable XHTML page, every table a folder of pages with generated index navigation, every write atomic.
///
/// ## Boundary
/// <!-- writing -->
/// - `put-record` — store the fields under a key, replacing whatever the key held, creating the table on first use
/// - `append-record` — store the fields under the key, or under the next free suffixed key when it is taken, and answer the key used
///
/// <!-- reading -->
/// - `get-record` — return the record under a key, or that there is none
/// - `list-keys` — return the keys of a table in ascending order
/// - `list-records` — return every record of a table in ascending key order
/// - `list-tables` — return the names of every table in ascending order
///
/// <!-- removing -->
/// - `remove-record` — delete the record under a key and answer whether one was there
/// - `remove-table` — delete a table with every record it holds
///
/// ## Requirements
///
/// ### R1: Name tables and keys
/// - R1.1 — The BC shall accept a table or key name made of letters, digits, underscore and hyphen that does not start with a hyphen.
/// - R1.2 — If a table or key name is absent, empty or contains any other character, then the BC shall reject the operation. _(why: names become folder and file names; a separator or a dot pair would escape the store)_
/// - R1.3 — If a table or key name is `index`, then the BC shall reject the operation. _(why: `index` is the generated navigation page of every folder)_
/// - R1.4 — The BC shall apply the name rules to every operation, reading and removing as well as writing. _(why: a lenient read resolves a hostile key outside the table folder)_
///
/// ### R2: Put a record
/// - R2.1 — When a table, a key and fields are supplied, the BC shall store the record under the key, creating the table if it does not exist.
/// - R2.2 — While a record exists under the key, when fields are put under it, the BC shall replace the whole record with the supplied fields. _(why: the embedded store replaces, never merges — D1)_
/// - R2.3 — The BC shall accept any text as a field name or value — empty values, line breaks, markup characters, characters beyond the basic plane — and give it back unchanged on read.
/// - R2.4 — If a field name or value contains a character the page format cannot represent at all, then the BC shall store it without that character rather than fail. _(why: one stray control character must not make a whole page unreadable)_
/// - R2.5 — The BC shall write a record in one atomic replacement of its page, so that a reader never observes a partly written page and no temporary file remains once the write completes. _(why: a browser, git or another agent may read the folder at any moment)_
///
/// ### R3: Append a record
/// - R3.1 — When a table, a key and fields are supplied and no record exists under the key, the BC shall store the record under that key and answer that key.
/// - R3.2 — If a record exists under the key, then the BC shall store the new record under the key with the lowest free numeric suffix starting at two (`key-2`, `key-3`, …) and answer that key. _(why: keys derived from a coarse timestamp collide within one second)_
/// - R3.3 — While a record exists under the key, when a record is appended under it, the BC shall leave the existing record untouched.
///
/// ### R4: Read records
/// - R4.1 — When a table and a key are supplied and a record exists under them, the BC shall return the record with its key and fields.
/// - R4.2 — If no record exists under the key, or the table does not exist, then the BC shall answer that there is none rather than fail.
/// - R4.3 — When a table is supplied, the BC shall return its keys in ascending order.
/// - R4.4 — When a table is supplied, the BC shall return its records in ascending key order.
/// - R4.5 — If the table does not exist, then the BC shall answer an empty listing rather than fail.
/// - R4.6 — The BC shall list as keys only record pages, never the generated index page nor any other file in the table folder.
/// - R4.7 — If a record's page is malformed, then the BC shall fail the read naming the page. _(why: a hand edit that broke a page must be loud; hiding the record would lose data silently)_
///
/// ### R5: Remove records and tables
/// - R5.1 — When a table and a key name an existing record, the BC shall delete it and answer that it did.
/// - R5.2 — If no record exists under the key, then the BC shall answer that nothing was removed rather than fail.
/// - R5.3 — When a table is supplied, the BC shall delete the table with every record it holds.
/// - R5.4 — If the table does not exist, then the BC shall complete the removal without failing.
///
/// ### R6: List tables
/// - R6.1 — The BC shall return the table names in ascending order.
/// - R6.2 — The BC shall count as a table only a folder that carries a generated index page. _(why: a foreign folder inside the root is not data)_
///
/// ### R7: Keep the store browsable
/// - R7.1 — The BC shall write every record as a page that is at once valid HTML and well-formed XML, holding each field as a name and value definition pair.
/// - R7.2 — When a record is stored or removed, the BC shall regenerate the table's index page linking every record and the root index page linking every table. _(why: the folder is a website readable without this component)_
/// - R7.3 — The BC shall link every record page back to its table index and every table index back to the root index.
/// - R7.4 — The BC shall title the root index after the folder holding the store.
///
/// ### R8: Open a store
/// - R8.1 — When a store is opened at a root folder, the BC shall create the folder if it does not exist.
///
/// ## Entities
/// - Entry
///
/// ## Decisions
/// - D1 — `htmldb` is the embedded store of zhtmldb only: table and record pages, generated indexes, atomic writes. _(why: the callers — transcripts, episodic memory, improvements — need a durable, browsable, diffable record store and nothing more; rejected: the CLI, positional columns and configuration handling, search by term, per-field merge, append and removal, which a caller composes on top when it needs them)_
///
/// ## References
/// - [htmldb](https://github.com/AdamBien/htmldb) — the page format and folder layout this store is derived from (governs R7)
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - The CLI, positional columns and configuration handling of zhtmldb (D1)
/// - Search by term over keys and field values (D1)
/// - Per-field merge, append and removal; a record is always replaced whole (D1)
/// - Key abbreviation — a key names exactly one record
/// - Coordinating concurrent writers beyond the atomic replacement of a page; the last put of a key wins
/// - Restricting field names; only table and key names carry a grammar
/// - Secrets and encryption — pages are plain text meant to be committed and browsed
package airhacks.zsmith.htmldb;
