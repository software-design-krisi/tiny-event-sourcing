package ru.quipy.bankDemo.transfers.subscribers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.quipy.bankDemo.accounts.api.*
import ru.quipy.bankDemo.transfers.api.TransferTransactionAggregate
import ru.quipy.bankDemo.transfers.api.TransferTransactionCreatedEvent
import ru.quipy.bankDemo.accounts.logic.Account
import ru.quipy.bankDemo.transfers.logic.TransferTransaction
import ru.quipy.core.EventSourcingService
import ru.quipy.streams.AggregateSubscriptionsManager
import java.util.*
import javax.annotation.PostConstruct
import ru.quipy.saga.SagaManager

@Component
class BankAccountsSubscriber(
    private val subscriptionsManager: AggregateSubscriptionsManager,
    private val transactionEsService: EventSourcingService<UUID, TransferTransactionAggregate, TransferTransaction>,
    private val sagaManager: SagaManager
) {
    private val logger: Logger = LoggerFactory.getLogger(BankAccountsSubscriber::class.java)

    @PostConstruct
    fun init() {
        subscriptionsManager.createSubscriber(AccountAggregate::class, "transactions::bank-accounts-subscriber") {
            `when`(TransferTransactionAcceptedEvent::class){event ->
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .launchSaga("PROCESS PARTICIPANT ACCEPT", "process participant accept")
                    .performSagaStep("INITIATE TRANSFER BETWEEN ACCOUNTS", "initiate transfer between accounts")
                    .performSagaStep("CREATING TRANSACTION FROM AND TO", "creating transaction from and to")
                    .sagaContext
                transactionEsService.update(
                    aggregateId = event.transactionId,
                    sagaContext = sagaContext
                ) {
                    it.transactionWithdrawalConfirmed(event.bankAccountId)
                }
            }
        }
    }
}