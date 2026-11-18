package com.ahmed.chatflow.repository;

import com.ahmed.chatflow.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Optional<Message> findBySenderIdAndClientId(Long senderId, String clientId);

    @Query("select count(m) from Message m "
            + "where m.conversation.id = :conversationId and m.sender.id <> :userId and m.createdAt > :since")
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("userId") Long userId,
                     @Param("since") Instant since);
}
