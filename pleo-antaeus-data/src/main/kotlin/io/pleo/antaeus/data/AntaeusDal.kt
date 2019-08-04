/*
    Implements the data access layer (DAL).
    This file implements the database queries used to fetch and insert rows in our database tables.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.BillingHistory
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    // selects invoices by status (paid or pending)
    fun fetchInvoices(invoiceStatus: InvoiceStatus): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .select { InvoiceTable.status.eq(invoiceStatus.name) }
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id!!)
    }

    // creates a billing history record for the given first of the month date
    // (could add logic to assert startDate is at 00:00:00 1/MM/YY)
    fun createBillingStart(startDate: Date): Int {
       val id = transaction(db) {
          BillingHistoryTable
              .insert {
                 it[this.start] = startDate.getTime()
              } get BillingHistoryTable.id
       }
       return id!!
    }

    // get billing payment history for the given first of the month date
    fun fetchBillingHistory(startDate: Date): BillingHistory? {
        return transaction(db) {
            BillingHistoryTable
                .select { BillingHistoryTable.start.eq(startDate.time) }
                .firstOrNull()
                ?.toBillingHistory()
        }
    }

    // set finish time for the billing cycle, can be used to determine how
    // long it takes to process a month's worth of payments, more importantly
    // the finish column tells us if the last billing cycle completed or not
    fun updateBillingFinish(id: Int, finishDate: Date) {
       transaction(db) {
          BillingHistoryTable
           .update  ({ BillingHistoryTable.id eq id }) {
              it[this.finish] = finishDate.getTime()
           }
       }
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id!!)
    }
}
