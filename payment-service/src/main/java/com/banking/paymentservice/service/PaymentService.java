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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository  paymentRepository;
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
        orderRequest.put("Currency","USD/INR");
        orderRequest.put("receipt","rec_" + System.currentTimeMillis() + UUID.randomUUID().toString()
                .replace("-","").substring(0,10));
        Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        log.info("Payment Order Created: {}", razorpayOrder.get("Id").toString());

      // Save Payment Record
        Payment payment = new Payment();
        payment.setRazorpayOrderId(razorpayOrder.get("Id").toString());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setCurrency("USD/INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(request.getDescription());

        Payment saved = paymentRepository.save(payment);

        return new PaymentOrderResponse(
                saved.getId(),
                razorpayOrder.get("id").toString(),
                request.getAmount(),
                "INR",
                keyId,
                "CREATED"
        );
        

    }
}
