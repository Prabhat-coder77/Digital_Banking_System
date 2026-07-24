package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private static SecureRandom secureRandom = new SecureRandom();


    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating Account for: {} ", request.getEmail());

        if(accountRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Account already exists: " +request.getEmail());
        }

        Account account = new Account();
        account.setAccountHolderName(request.getAccountHolderName());
        account.setEmail(request.getEmail());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS
                        ? new BigDecimal("100000")
                        : new BigDecimal("500000")
        );
        Account savedAccount = accountRepository.save(account);
        log.info("Account Created : {} ", savedAccount.getAccountNumber());
        return mapToResponse(savedAccount);

    }

    public AccountResponse getAccount(String accountNumber) {
     Account account = accountRepository.findByAccountNumber(accountNumber)
             .orElseThrow(()-> new RuntimeException("Account not found: " +accountNumber));

     return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account not found: " +accountNumber));

        return account.getBalance();
    }

    /*
       Block account - called by fraud-detection service via kafka
       @Param accountNumber
     */
    public void blockAccount(String accountNumber) {
    log.info("Blocking Account for: {} ", accountNumber);
    Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account not found: " +accountNumber));
    account.setStatus(AccountStatus.BLOCKED);
    accountRepository.save(account);
    log.info("Account blocked: {}", accountNumber);

    }
    /*
       Deduct Balance from sender account,
       called by Transaction service
       @Param accountNumber
       @Param amount
     */
    public void deductBalance(String accountNumber, BigDecimal amount) {
      log.info("Deducting Balance {} from Account: {} ",amount ,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account not found: " +accountNumber));
        if(account.getStatus() !=  AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active"+accountNumber);
        }
        if(account.getBalance().compareTo(amount) <= 0) {
             throw new RuntimeException("Insufficient Balance Of This Account: "+accountNumber);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        log.info("Account Balance Updated New Balance: {}", account.getBalance());

    }
    /*
       Credit Balance,
       called by Transaction service via kafka
       @Param accountNumber
       @Param amount
     */
    public void creditBalance(String accountNumber, BigDecimal amount) {
      log.info("Credit Balance {} to Account: {} ",amount ,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(()-> new RuntimeException("Account not found: " +accountNumber));
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("Balance credited. New Balance: {}", account.getBalance());
    }



    // generate 12- digit account number
    private String generateAccountNumber(){
        String accountNumber;
        do{
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        }while (accountRepository.existsByAccountNumber(accountNumber));
            return accountNumber;

    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountHolderName(account.getAccountHolderName());
        response.setAccountNumber(account.getAccountNumber());
        response.setEmail(account.getEmail());
        response.setPhone(account.getPhone());
        response.setAccountType(account.getAccountType());
        response.setStatus(account.getStatus());
        response.setBalance(account.getBalance());
        response.setDailyTransactionLimit(account.getDailyTransactionLimit());
        response.setCreatedAt(account.getCreatedAt());

        return response;
    }


}
