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
            `when`(TransferWithdrawalSucceededEvent::class){event ->
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("WITHDRAWAL_SUCCEEDED", "withdrawal for transaction is succeeded")
                    .sagaContext

                transactionEsService.update(
                    aggregateId = event.transactionId,
                    sagaContext = sagaContext
                ) {
                    it.transactionWithdrawalSucceeded(event.bankAccountId)
                }
            }
            `when`(TransferWithdrawalFailedEvent::class){event ->
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("WITHDRAWAL_FAILED", "withdrawal for transaction is failed")
                    .sagaContext

                transactionEsService.update(
                    aggregateId = event.transactionId,
                    sagaContext = sagaContext
                ) {
                    it.transactionWithdrawalFailed(event.bankAccountId)
                }
            }
            `when`(TransferDepositSuccededEvent::class){event ->
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("DEPOSIT_SUCCEEDED", "deposit for transaction is succeeded")
                    .sagaContext

                transactionEsService.update(
                    aggregateId = event.transactionId,
                    sagaContext = sagaContext
                ) {
                    it.transactionDepositSucceeded(event.bankAccountId)
                }
            }
            `when`(TransferDepositFailedEvent::class){event ->
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("DEPOSIT_FAILED", "deposit for transaction is failed")
                    .sagaContext

                transactionEsService.update(
                    aggregateId = event.transactionId,
                    sagaContext = sagaContext
                ) {
                    it.transactionDepositFailed(event.bankAccountId)
                }
            }
        }
    }
}