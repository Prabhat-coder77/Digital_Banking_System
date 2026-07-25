package com.banking.paymentservice.dto;

import com.banking.paymentservice.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {

    private String razorpayOrderId;
    private String paymentId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String razorpayKeyId;

}
