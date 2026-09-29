package airhacks.zsmith.htmldb;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Generated from the capability spec in [airhacks.zsmith.htmldb] — do not edit.
/// Marks the boundary method or test that realizes the given requirement statements.
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Requirement {

    /// One constant per statement id in the spec's `## Requirements`.
    enum Rn {
        /// The BC shall accept a table or key name made of letters, digits, underscore and hyphen that does not start with a hyphen.
        R1_1("R1.1", "The BC shall accept a table or key name made of letters, digits, underscore and hyphen that does not start with a hyphen."),
        /// If a table or key name is absent, empty or contains any other character, then the BC shall reject the operation.
        R1_2("R1.2", "If a table or key name is absent, empty or contains any other character, then the BC shall reject the operation."),
        /// If a table or key name is `index`, then the BC shall reject the operation.
        R1_3("R1.3", "If a table or key name is `index`, then the BC shall reject the operation."),
        /// The BC shall apply the name rules to every operation, reading and removing as well as writing.
        R1_4("R1.4", "The BC shall apply the name rules to every operation, reading and removing as well as writing."),

        /// When a table, a key and fields are supplied, the BC shall store the record under the key, creating the table if it does not exist.
        R2_1("R2.1", "When a table, a key and fields are supplied, the BC shall store the record under the key, creating the table if it does not exist."),
        /// While a record exists under the key, when fields are put under it, the BC shall replace the whole record with the supplied fields.
        R2_2("R2.2", "While a record exists under the key, when fields are put under it, the BC shall replace the whole record with the supplied fields."),
        /// The BC shall accept any text as a field name or value — empty values, line breaks, markup characters, characters beyond the basic plane — and give it back unchanged on read.
        R2_3("R2.3", "The BC shall accept any text as a field name or value — empty values, line breaks, markup characters, characters beyond the basic plane — and give it back unchanged on read."),
        /// If a field name or value contains a character the page format cannot represent at all, then the BC shall store it without that character rather than fail.
        R2_4("R2.4", "If a field name or value contains a character the page format cannot represent at all, then the BC shall store it without that character rather than fail."),
        /// The BC shall write a record in one atomic replacement of its page, so that a reader never observes a partly written page and no temporary file remains once the write completes.
        R2_5("R2.5", "The BC shall write a record in one atomic replacement of its page, so that a reader never observes a partly written page and no temporary file remains once the write completes."),

        /// When a table, a key and fields are supplied and no record exists under the key, the BC shall store the record under that key and answer that key.
        R3_1("R3.1", "When a table, a key and fields are supplied and no record exists under the key, the BC shall store the record under that key and answer that key."),
        /// If a record exists under the key, then the BC shall store the new record under the key with the lowest free numeric suffix starting at two (`key-2`, `key-3`, …) and answer that key.
        R3_2("R3.2", "If a record exists under the key, then the BC shall store the new record under the key with the lowest free numeric suffix starting at two (`key-2`, `key-3`, …) and answer that key."),
        /// While a record exists under the key, when a record is appended under it, the BC shall leave the existing record untouched.
        R3_3("R3.3", "While a record exists under the key, when a record is appended under it, the BC shall leave the existing record untouched."),

        /// When a table and a key are supplied and a record exists under them, the BC shall return the record with its key and fields.
        R4_1("R4.1", "When a table and a key are supplied and a record exists under them, the BC shall return the record with its key and fields."),
        /// If no record exists under the key, or the table does not exist, then the BC shall answer that there is none rather than fail.
        R4_2("R4.2", "If no record exists under the key, or the table does not exist, then the BC shall answer that there is none rather than fail."),
        /// When a table is supplied, the BC shall return its keys in ascending order.
        R4_3("R4.3", "When a table is supplied, the BC shall return its keys in ascending order."),
        /// When a table is supplied, the BC shall return its records in ascending key order.
        R4_4("R4.4", "When a table is supplied, the BC shall return its records in ascending key order."),
        /// If the table does not exist, then the BC shall answer an empty listing rather than fail.
        R4_5("R4.5", "If the table does not exist, then the BC shall answer an empty listing rather than fail."),
        /// The BC shall list as keys only record pages, never the generated index page nor any other file in the table folder.
        R4_6("R4.6", "The BC shall list as keys only record pages, never the generated index page nor any other file in the table folder."),
        /// If a record's page is malformed, then the BC shall fail the read naming the page.
        R4_7("R4.7", "If a record's page is malformed, then the BC shall fail the read naming the page."),

        /// When a table and a key name an existing record, the BC shall delete it and answer that it did.
        R5_1("R5.1", "When a table and a key name an existing record, the BC shall delete it and answer that it did."),
        /// If no record exists under the key, then the BC shall answer that nothing was removed rather than fail.
        R5_2("R5.2", "If no record exists under the key, then the BC shall answer that nothing was removed rather than fail."),
        /// When a table is supplied, the BC shall delete the table with every record it holds.
        R5_3("R5.3", "When a table is supplied, the BC shall delete the table with every record it holds."),
        /// If the table does not exist, then the BC shall complete the removal without failing.
        R5_4("R5.4", "If the table does not exist, then the BC shall complete the removal without failing."),

        /// The BC shall return the table names in ascending order.
        R6_1("R6.1", "The BC shall return the table names in ascending order."),
        /// The BC shall count as a table only a folder that carries a generated index page.
        R6_2("R6.2", "The BC shall count as a table only a folder that carries a generated index page."),

        /// The BC shall write every record as a page that is at once valid HTML and well-formed XML, holding each field as a name and value definition pair.
        R7_1("R7.1", "The BC shall write every record as a page that is at once valid HTML and well-formed XML, holding each field as a name and value definition pair."),
        /// When a record is stored or removed, the BC shall regenerate the table's index page linking every record and the root index page linking every table.
        R7_2("R7.2", "When a record is stored or removed, the BC shall regenerate the table's index page linking every record and the root index page linking every table."),
        /// The BC shall link every record page back to its table index and every table index back to the root index.
        R7_3("R7.3", "The BC shall link every record page back to its table index and every table index back to the root index."),
        /// The BC shall title the root index after the folder holding the store.
        R7_4("R7.4", "The BC shall title the root index after the folder holding the store."),

        /// When a store is opened at a root folder, the BC shall create the folder if it does not exist.
        R8_1("R8.1", "When a store is opened at a root folder, the BC shall create the folder if it does not exist.");

        private final String id;
        private final String statement;

        Rn(String id, String statement) {
            this.id = id;
            this.statement = statement;
        }

        public String statement() {
            return this.statement;
        }

        @Override
        public String toString() {
            return this.id;
        }
    }

    Rn[] value();
}
