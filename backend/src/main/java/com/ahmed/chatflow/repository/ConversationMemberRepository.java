package com.ahmed.chatflow.repository;

import com.ahmed.chatflow.entity.ConversationMember;
import com.ahmed.chatflow.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    List<ConversationMember> findAllByConversationId(Long conversationId);

    long countByConversationIdAndRole(Long conversationId, MemberRole role);
}
