package com.banking.accountservice.dto;

import com.banking.accountservice.entity.AccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountRequest {

    @NotBlank(message ="Account Holder Name is required")
    private String accountHolderName;

    @NotBlank(message= "Email is required")
    @Email(message = "Enter Valid Email")
    private String email;
    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotNull(message = "Account Type is required")
    private AccountType accountType;

    @NotNull(message = "Initial deposit is required")
    @Positive(message = "Initial deposit must be positive")
    private BigDecimal initialDeposit;


}
