package com.bits.payment.repository;

import com.bits.payment.model.PaymentTransaction;
import com.bits.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByPaymentId(String paymentId);
    List<PaymentTransaction> findByOrderId(String orderId);
    List<PaymentTransaction> findByUserId(String userId);
    List<PaymentTransaction> findByStatus(PaymentStatus status);
    boolean existsByPaymentId(String paymentId);
}
