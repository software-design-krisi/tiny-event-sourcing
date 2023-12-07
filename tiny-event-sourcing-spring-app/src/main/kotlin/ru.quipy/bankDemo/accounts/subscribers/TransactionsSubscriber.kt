package ru.quipy.bankDemo.accounts.subscribers

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.quipy.bankDemo.accounts.api.AccountAggregate
import ru.quipy.bankDemo.transfers.api.TransferTransactionAggregate
import ru.quipy.bankDemo.transfers.api.TransferTransactionCreatedEvent
import ru.quipy.bankDemo.accounts.logic.Account
import ru.quipy.bankDemo.transfers.api.*
import ru.quipy.core.EventSourcingService
import ru.quipy.streams.AggregateSubscriptionsManager
import java.util.*
import javax.annotation.PostConstruct
import ru.quipy.saga.SagaManager

@Component
class TransactionsSubscriber(
    private val subscriptionsManager: AggregateSubscriptionsManager,
    private val accountEsService: EventSourcingService<UUID, AccountAggregate, Account>,
    private val sagaManager: SagaManager
) {
    private val logger: Logger = LoggerFactory.getLogger(TransactionsSubscriber::class.java)
    @PostConstruct
    fun init() {
        subscriptionsManager.createSubscriber(TransferTransactionAggregate::class, "accounts::transaction-processing-subscriber") {
            `when`(TransferTransactionCreatedEvent::class) { event ->
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .launchSaga("TRANSFER_TRANSACTION", "transfer transaction started")
                    .performSagaStep("TRANSACTION_FROM_AND_TO_CREATED", "creating transaction from and to")
                    .sagaContext

                logger.info("Got transaction to process: $event")

                val transactionOutcome1 = accountEsService.update(event.sourceAccountId, sagaContext) {
                    it.transferFrom(
                        event.sourceBankAccountId,
                        event.transferId,
                        event.transferAmount
                    )
                }

                val transactionOutcome2 = accountEsService.update(event.destinationAccountId, sagaContext) {
                    it.transferTo(
                        event.destinationBankAccountId,
                        event.transferId,
                        event.transferAmount
                    )
                }

                logger.info("Transaction: ${event.transferId}. Outcomes: $transactionOutcome1, $transactionOutcome2")
            }
            `when`(TransactionSucceededEvent::class) { event ->
                logger.info("Got transaction succeeded event: $event")
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("TRANSACTION_SUCCEEDED", "transaction completed successful")
                    .sagaContext

                val transactionOutcome1 = accountEsService.update(event.sourceAccountId, sagaContext) {
                    it.processPendingTransaction(event.sourceBankAccountId, event.transferId)
                }

                val transactionOutcome2 = accountEsService.update(event.destinationAccountId, sagaContext) {
                    it.processPendingTransaction(event.destinationBankAccountId, event.transferId)
                }

                logger.info("Transaction: ${event.transferId}. Outcomes: $transactionOutcome1, $transactionOutcome2")
            }
            `when`(TransactionFailedEvent::class) { event ->
                logger.info("Got transaction failed event: $event")
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("TRANSACTION_FAILED", "transaction failed")
                    .sagaContext

                val transactionOutcome1 = accountEsService.update(event.sourceAccountId, sagaContext) {
                    it.processPendingTransaction(event.sourceBankAccountId, event.transferId)
                }

                val transactionOutcome2 = accountEsService.update(event.destinationAccountId, sagaContext) {
                    it.processPendingTransaction(event.destinationBankAccountId, event.transferId)
                }

                logger.info("Transaction: ${event.transferId}. Outcomes: $transactionOutcome1, $transactionOutcome2")
            }
            `when`(TransactionWithdrawalFailedEvent::class) { event ->
                logger.info("Got transaction withdrawal failed event: $event")
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("TRANSACTION_FAILED", "transaction failed")
                    .sagaContext

                val transactionOutcome1 = accountEsService.update(event.sourceAccountId, sagaContext) {
                    it.processPendingTransaction(event.sourceBankAccountId, event.transferId)
                }

                val transactionOutcome2 = accountEsService.update(event.destinationAccountId, sagaContext) {
                    it.rollbackPendingTransaction(event.destinationBankAccountId, event.transferId)
                }

                logger.info("Transaction: ${event.transferId}. Outcomes: $transactionOutcome1, $transactionOutcome2")
            }
            `when`(TransactionDepositFailedEvent::class) { event ->
                logger.info("Got transaction deposit failed event: $event")
                val sagaContext = sagaManager
                    .withContextGiven(event.sagaContext)
                    .performSagaStep("TRANSACTION_FAILED", "transaction failed")
                    .sagaContext

                val transactionOutcome1 = accountEsService.update(event.sourceAccountId, sagaContext) {
                    it.rollbackPendingTransaction(event.sourceBankAccountId, event.transferId)
                }

                val transactionOutcome2 = accountEsService.update(event.destinationAccountId, sagaContext) {
                    it.processPendingTransaction(event.destinationBankAccountId, event.transferId)
                }

                logger.info("Transaction: ${event.transferId}. Outcomes: $transactionOutcome1, $transactionOutcome2")
            }
        }
    }
}