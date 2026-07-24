package com.banking.transactionservice.entity;

/*
   Transaction LifeCycle Flow:
   Pending-->Processing->Completed(if clean transaction)
                       ->PENDING.verification(if suspicious detected)
                                 ->Completed(if verified)
                                 ->FLAGGED (SAGA refund)
                       ->FAILED
                       ->FLAGGED
 */

public enum TransactionStatus {

    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED

}
