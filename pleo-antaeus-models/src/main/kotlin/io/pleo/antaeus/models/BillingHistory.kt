package io.pleo.antaeus.models

import java.util.Date

data class BillingHistory(
   val id: Int,
   val start: Date,
   val finish: Date
)

