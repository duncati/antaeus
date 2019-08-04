package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.NetworkException
import java.util.Date
import java.util.Calendar
import java.util.Timer
import kotlin.concurrent.schedule
import mu.KotlinLogging

class BillingService(private val dal: AntaeusDal, val paymentProvider: PaymentProvider) {

    private val logger = KotlinLogging.logger {}

    // the main and only public function in BillingService, this schedules or runs the next
    // payment batch of invoices on the first of the month (and subsequent months)
    fun scheduleNextPayments() {
       val firstOfTheMonth = Calendar.getInstance()
       // on the very highly unlikely chance that firstOfTheMonth was created at the stroke of 
       // midnight on the 1st of the month then run billing right now
       if (firstOfTheMonth.get(Calendar.DAY_OF_MONTH) == 1 &&
           firstOfTheMonth.get(Calendar.HOUR_OF_DAY) == 0 &&
           firstOfTheMonth.get(Calendar.MINUTE) == 0 &&
           firstOfTheMonth.get(Calendar.SECOND) == 0 &&
           firstOfTheMonth.get(Calendar.MILLISECOND) == 0) {
              runNormalBilling(firstOfTheMonth)
       }

       // otherwise set firstOfTheMonth to the beginning of this month
       firstOfTheMonth.set(Calendar.HOUR_OF_DAY, 0)
       firstOfTheMonth.set(Calendar.MINUTE, 0)
       firstOfTheMonth.set(Calendar.SECOND, 0)
       firstOfTheMonth.set(Calendar.MILLISECOND, 0)
       firstOfTheMonth.set(Calendar.DAY_OF_MONTH, 1)

       // see if this billing cycle has already been started (this solution can work across
       // servers iff they share the same concurrent database, a locking mechanism would need
       // to be implemented to guarantee only one runs the billing cycle, this all depends
       // on how this service is meant to be deployed and run)
       val history = dal.fetchBillingHistory(firstOfTheMonth.time)
       if (history == null) {
          // the last billing cycle was missed! schedule it now? sure why not!
          logger.warn("BillingService missed scheduling payments for the first of this month, running bill processing now")
          runNormalBilling(firstOfTheMonth)

       } else if (history.finish.time == 0L) {
          // last billing cycle was incomplete! continue it now? there's confusion here - does 
          // the payment provider service update InvoiceTable changing PENDING status to PAID? 
          // if so we can continue to run billing
          logger.warn("BillingService billing process appears to have been interrupted, continuing to process Pending Invoices")
          continueBilling(billingHistoryId = history.id)

       } else {
          // normal use case, last billing cycle was complete so schedule the next one
          firstOfTheMonth.add(Calendar.MONTH, 1)
          val timer = Timer("BillingSchedulePayments", true)
          val task = timer.schedule(firstOfTheMonth.time) {
             runNormalBilling(firstOfTheMonth)
          }
          logger.info("BillingService scheduled billing to run at ${Date(task.scheduledExecutionTime())}")
       }
    }

    // normal billing case: create a history record, process payments, finish history record
    private fun runNormalBilling(firstOfTheMonth: Calendar) {
        val billingHistoryId = createBillingStart(firstOfTheMonth.time)
        runBilling()
        finishBilling(billingHistoryId = billingHistoryId)
    }

    // abnormal use case if billing crashed somehow and needs continuation,
    // do NOT create billing record but _do_ close billing record when complete
    private fun continueBilling(billingHistoryId: Int) {
       runBilling()
       finishBilling(billingHistoryId = billingHistoryId)
    }

    // Creates start record for bill processing for the given 1st of the month
    private fun createBillingStart(startDate: Date): Int {
        return dal.createBillingStart(startDate = startDate)
    }

    // Completes billing record by setting the finish time
    private fun finishBilling(billingHistoryId: Int) {
        dal.updateBillingFinish(id = billingHistoryId, finishDate = Date())
    }

    // Iterates pending invoices requesting charge from external PaymentProvider
    private fun runBilling() {
        val invoices: List<Invoice> = dal.fetchInvoices(invoiceStatus = InvoiceStatus.PENDING)
        for (invoice in invoices) {
            try {
                if (!paymentProvider.charge(invoice = invoice)) {
                   logger.error("Customer Account Balance did not allow charge for customer id ${invoice.customerId}")
                }
            }
            catch (e: CustomerNotFoundException) {
               logger.error(e) {"Customer Not Found for customer id ${invoice.customerId} on invoice ${invoice.id}"}
            }
            catch (e: CurrencyMismatchException) {
               logger.error(e) {"Currency Mismatch for customer id ${invoice.customerId}, expected ${invoice.amount.currency}"}
            }
            catch (e: NetworkException) {
               logger.error(e) {"Network Error"}
            }
        }
        // once complete schedule the next month's cycle, this line is subtle but key
        // (otherwise unless the service is restarted there won't be a next billing cycle)
        scheduleNextPayments()
    }
}
