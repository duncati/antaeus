## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## My Thought Process

Billing must be managed and executed correctly otherwise customers lose trust in a company. Ensuring that customers are billed on time and exactly once (certainly no more than once!) is crucial. Without understanding how the BillingService is intended to be deployed (stand alone, distributed, etc.) I opted to create a BillingHistoryTable to give some level of tracking to the service. Ideally this would be in a distributed database so other BillingServices see each other. An added bonus to this approach is having a historical record of payment processing as well as knowing how long it takes to process normally. This run time duration only in the normal use case, to know exact processing time we'd need to add another column to store when billing actually started. From this we can observe potential issues and optimize. For instance we could thread and batch calls to the external PaymentProvider (so as not to overload it) since it is called serially for now. 

The BillingService handles 3 scenarios:

1. What if payment processing wasn't run on the 1st of the current month? Run it now?
2. What if payment processing was started and did not finish? Continue it?
3. The normal use case; this month's billing completed so schedule next month's on the 1st

I took some liberties with the first 2 scenarios opting to run payment processing for both. This helped in testing the service locally by hand. By deleting the billing history table, payment processing runs immediately (scenario 1). By updating the finish column to 0 (it's default value on row creation) payment processing attempts to continue where it left off (scenario 2). And once there's a billing history entry for the current month the Billing Service schedules the next payment processing time which is logged for visibility (scenario 3).

I started writing a BillingServiceTest class to accomplish the above and deleted it because in reality my interpretation of this BillingService isn't ready yet. The open ended nature of this challenge in the real world would've resulted in meetings to clarify requirements and decide how the BillingService should operate in different conditions. My approach yielded one public function that attempted to robustly handle different use cases based on some guesses and assumptions. But is it right? Does it meet the needs of the business owners? Does it meet the needs of the other services? What if instead of handling the 3 scenarios above it should throw exceptions for some other code to decide what to do? These decisions drastically change the service and thus how a test harness operates. I opt to write test cases once there is some agreement that what is being tested is correct enough in the first place in order to proceed.

Billing Service exception handling has room to improve. NetworkExceptions may go away in a few seconds or minutes. Other than logging them perhaps a sleep and hope it gets better approach can work? Possible solutions here include a retry loop with increasing wait times, creating a list of failed Invoice charges and retrying them later, etc. Without knowing the transactionality of PaymentProvider.charge this retry logic can be error prone. What if the NetworkException is thrown after the PaymentProvider completed the charge? We certainly do not want to charge the customer again. Thus these solutions require more information and understanding to safely proceed. The other two exceptions where there are serious data issues (customer not found and currency mismatch) require data correction likely through human intervention. I chose to log these errors and move on to process other invoices for this initial approach.

It is also noteworthy that my commits happened all at once and without comments. With a personal and corporate history of automated build servers I have a habit of only committing code that is complete and compilable, even in this sandboxed fork. Ideally I would have written comments for each commit as I went along. I leave this readme file as one big commit comment. =)

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.


*Running through docker*

Install docker for your platform

```
make docker-run
```

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```


### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the "rest api" models used throughout the application.
|
├── pleo-antaeus-rest
|        Entry point for REST API. This is where the routes are defined.
└──
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!
