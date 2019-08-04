/*
    Defines database tables and their schemas.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import org.jetbrains.exposed.sql.Table

object InvoiceTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
    val value = decimal("value", 1000, 2)
    val customerId = reference("customer_id", CustomerTable.id)
    val status = text("status")
}

object CustomerTable : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val currency = varchar("currency", 3)
}

object BillingHistoryTable : Table() {
   val id = integer("id").autoIncrement().primaryKey()
   // ideally the start and finish columns would be date or datetime, 
   // after many failed attempts I opted to move forward with long
   // (numerous compile errors around date/String/long inferred mismatches)
   val start = long("start").uniqueIndex()
   val finish = long("finish").default(0)
}
