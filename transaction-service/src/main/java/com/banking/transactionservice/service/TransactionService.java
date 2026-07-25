package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient  accountServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "transaction.detected";


    /**
     * SAGA Step-1: Initiate transfer
     * Deducted from sender via feign
     * Saves transaction as PROCESSING
     * Publish Event to kafka for fraud check
     * Returns
     * @Param request
     * @return
     */

    public TransactionResponse transfer(TransferRequest request) {
        log.info("SAGA Starts -Transfer: {} -> {} amount: {}",
                request.getSenderAccountNumber(),
                request.getReceiverAccountNumber(),
                request.getAmount());

        // SAGA Step-1 : Deducted from sender
        accountServiceClient.deductBalance(request.getSenderAccountNumber(),request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(request.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(request.getReceiverAccountNumber());
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(request.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        Transaction save = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING: {}", save.getId());

        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                save.getId(),
                save.getSenderAccountNumber(),
                save.getReceiverAccountNumber(),
                save.getAmount(),
                save.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC,save.getId(),event);
        log.info("SAGA Step-2 TransactionInitiatedEvent Published: {}", save.getId());

        return mapToResponse(save);
    }

    public TransactionResponse getTransaction(String transactionId) {

        return mapToResponse(
                transactionRepository.findById(transactionId)
                        .orElseThrow(()->new RuntimeException("Transaction Not Found: "+transactionId)));

    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
      return transactionRepository
              .findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
              .stream()
              .map(this::mapToResponse)
              .collect(Collectors.toList());
    }

    public TransactionResponse verifyOtp(String transactionId, String otp) {
        log.info("OTP Verification for Transaction: {}",transactionId);
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction Not Found: " + transactionId));
        String otpKey = "verification:otp" + transactionId;
        String storeOtp= redisTemplate.opsForValue().get(otpKey);

        if(storeOtp == null){
            // OTP Expired
            log.warn("OTP expired For Transaction: {}",transactionId);
            compensateTransaction(transaction, "OTP expired - Transaction cancel and amount refund");
            return mapToResponse(transaction);
        }
        if(storeOtp.equals(otp)){
            // BLOCK AND REFUNDING
            log.info("Wrong Otp - blocking and refunding: {}",transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,"Wrong Otp entered - transaction cancelled " + "Account blocked for security");
            return mapToResponse(transaction);

        }
        //OTP Correct - transaction complete
        log.info("OTP Verified - Transaction Completing: {}",transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }
    private void compensateTransaction(Transaction transaction,String reason)
    {
        log.info("SAGA Compensating Refunding: {} amount: {}",transaction.getSenderAccountNumber(),transaction.getAmount());
        // CREDIT MONEY BACKTO SENDER SYNCHRONOUSLY
         accountServiceClient.creditBalance(transaction.getSenderAccountNumber(), transaction.getAmount());
         transaction.setStatus(TransactionStatus.FLAGGED);
         transaction.setFailureReason(reason + " -SAGA COMPENSATION Executed, amount refunded at "+ LocalDateTime.now());
         transactionRepository.save(transaction);

         // Publish refund event - Notification service will alert user
        Map<Object, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount",transaction.getAmount());
        refundEvent.put("reason",reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId(),refundEvent);
        log.info("SAGA COMPENSATION COMPLETE - {} refund to: {}",transaction.getAmount(),transaction.getSenderAccountNumber());
    }
    private void blockAccountAndCompensate(Transaction transaction,String reason){
        HashMap<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason",reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(),fraudEvent);
        log.warn("fruad.detected published account: {} will be blocked kindly contact to the Bank",transaction.getSenderAccountNumber());

        //SAGA Compensation - refund Sender
        compensateTransaction(transaction,reason);

    }

    private void completeTransaction(Transaction  transaction){
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(transaction.getId(),
                transaction.getSenderAccountNumber(), transaction.getReceiverAccountNumber(),
                transaction.getAmount(), transaction.getDescription());

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC,transaction.getId(),completedEvent);
       log.info("SAGA COMPLETED- Transaction: {} completed: {}",transaction.getId(),transaction.getSenderAccountNumber());
    }

    public void processCleanResult(String transactionId){
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction Not Found: " + transactionId));

        if(transaction.getStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction {} not processing -skiping",transactionId);
            return;
        }
        completeTransaction(transaction);
    }


    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setSenderAccountNumber(transaction.getSenderAccountNumber());
        response.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        response.setAmount(transaction.getAmount());
        response.setType(transaction.getType());
        response.setStatus(transaction.getStatus());
        response.setDescription(transaction.getDescription());
        response.setReferenceNumber(transaction.getReferenceNumber());
        response.setFailureReason(transaction.getFailureReason());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setCompletedAt(transaction.getCompletedAt());

        return response;
    }



}
