/// Key-value persistence in which the storage format is also the UI: every record
/// is a browsable XHTML page, every table a folder of pages with generated index
/// navigation. Records are written one file at a time and atomically, so a crash
/// costs at most the record being written, concurrent writers do not overwrite each
/// other, and version control tracks changes per record.
///
/// Derived from htmldb (https://github.com/AdamBien/htmldb), reduced to the
/// embedded store; the CLI, positional columns and configuration handling are not
/// part of this component.
package airhacks.zsmith.htmldb;
