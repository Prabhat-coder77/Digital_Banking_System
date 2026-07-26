package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.banking.paymentservice.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository  paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${razorpay.key-id}")
    private String keyId;
    @Value("${razorpay.key-id}")
    private String keySecret;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    /**
     * Create RazorPay payment order
     * FLOW:
     *   1. Create Order in razorpay
     *   2. save payment record in DB
     *   3. Return Order Details to frontend
     *   4. Frontend show Razorpay checkout
     *   5. User pays
     *   6. Razorpays Calls webhook
     *   @param request
     *   @return
     */
    public PaymentOrderResponse createPaymentOrder(CreatePaymentRequest request) throws RazorpayException {

        log.info("Creating Payment Order for account: {} amount: {}",
                request.getAccountNumber(), request.getAmount());

        RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);
        // Converted Amount
        int convertedAmount = request.getAmount().multiply(BigDecimal.valueOf(100)).intValue();

        JSONObject orderRequest = new JSONObject().put("amount", convertedAmount);
        orderRequest.put("amount",convertedAmount);
        orderRequest.put("Currency","USD");
        orderRequest.put("receipt","rec_" + System.currentTimeMillis() + UUID.randomUUID().toString()
                .replace("-","").substring(0,10));
        Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        log.info("Payment Order Created: {}", razorpayOrder.get("Id").toString());

      // Save Payment Record
        Payment payment = new Payment();
        payment.setRazorpayOrderId(razorpayOrder.get("Id").toString());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(request.getDescription());

        Payment saved = paymentRepository.save(payment);

        return new PaymentOrderResponse(
                saved.getId(),
                razorpayOrder.get("id").toString(),
                request.getAmount(),
                "USD",
                keyId,
                "CREATED"
        );
    }

    public void handleWebHook(Map<String, Object> payload){
        log.info("Received Razorpay webhook: {}", payload.get("event"));

        String event = (String) payload.get("event");

        if("payment.captured".equals(event)){
            handlePaymentSuccess(payload);
        } else if ("payment.failed".equals(event)) {
            handlePaymentFailure(payload);
        }

    }

    private void handlePaymentSuccess(Map<String, Object> payload){
        try{

            Map<String,Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("orderId");
            String paymentId = (String) paymentData.get("paymentId");

           Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                   .orElseThrow(() ->new RuntimeException("Payment Not Found For Order: " + orderId));

           payment.setRazorpayOrderId(paymentId);
           payment.setStatus(PaymentStatus.COMPLETED);
           paymentRepository.save(payment);

           // Publish Payement completed event to kafka
            Map<String, Object> event = new HashMap<>();
            event.put("PaymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorPayPaymentId", paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, payment.getId() ,event);
            log.info("Payment Completed Successfully: {}",payment.getId());

        }catch (Exception e){
            log.error("Error Handling payment success: {}", e.getMessage());
        }
    }

    private void handlePaymentFailure(Map<String, Object> payload){
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);
            String orderId = (String) paymentData.get("order_id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException(
                            "Payment not found for order: " + orderId));

            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via Razorpay");
            paymentRepository.save(payment);

            // Publish payment.failed event ← ADD THIS
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("reason", "Payment failed via Razorpay");
            kafkaTemplate.send("payment.failed", payment.getId(), event);

            log.warn("Payment failed: {}", payment.getId());

        } catch (Exception e) {
            log.error("Error handling payment failure: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String, Object> entity = (Map<String, Object>) payload.get("payload");
        Map<String, Object> paymentWrapper = (Map<String, Object>) entity.get("payment");
        return (Map<String, Object>) paymentWrapper.get("entity");
    }




}
