package com.ahmed.chatflow.repository;

import com.ahmed.chatflow.entity.MessageReceipt;
import com.ahmed.chatflow.entity.ReceiptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, Long> {

    Optional<MessageReceipt> findByMessageIdAndUserId(Long messageId, Long userId);

    @Query("select max(r.updatedAt) from MessageReceipt r join r.message m "
            + "where m.conversation.id = :conversationId and r.user.id = :userId and r.status = :status")
    Optional<Instant> findLastReceiptAt(@Param("conversationId") Long conversationId,
                                        @Param("userId") Long userId,
                                        @Param("status") ReceiptStatus status);
}
