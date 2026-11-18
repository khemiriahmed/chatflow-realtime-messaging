package com.ahmed.chatflow.repository;

import com.ahmed.chatflow.entity.Conversation;
import com.ahmed.chatflow.entity.ConversationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<Conversation> findWithMembersById(Long id);

    @EntityGraph(attributePaths = {"members", "members.user"})
    @Query("select distinct c from Conversation c join c.members m where m.user.id = :userId "
            + "order by c.updatedAt desc, c.id desc")
    Page<Conversation> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("select c from Conversation c join c.members m1 join c.members m2 "
            + "where c.type = :type and m1.user.id = :firstUserId and m2.user.id = :secondUserId")
    Optional<Conversation> findPrivateBetween(@Param("type") ConversationType type,
                                              @Param("firstUserId") Long firstUserId,
                                              @Param("secondUserId") Long secondUserId);
}
