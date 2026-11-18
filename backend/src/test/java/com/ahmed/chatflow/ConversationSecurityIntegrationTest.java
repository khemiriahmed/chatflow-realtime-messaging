package com.ahmed.chatflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ahmed.chatflow.dto.AddMemberRequest;
import com.ahmed.chatflow.dto.CreateConversationRequest;
import com.ahmed.chatflow.dto.RegisterRequest;
import com.ahmed.chatflow.dto.SendMessageRequest;
import com.ahmed.chatflow.entity.ConversationType;
import com.ahmed.chatflow.exception.ForbiddenException;
import com.ahmed.chatflow.exception.ResourceNotFoundException;
import com.ahmed.chatflow.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MessageService messageService;

    @Test
    void privateConversationHistoryIsRestrictedToMembers() throws Exception {
        JsonNode alice = register("Alice");
        JsonNode bob = register("Bob");
        JsonNode carol = register("Carol");
        String aliceToken = alice.get("accessToken").asText();
        String bobToken = bob.get("accessToken").asText();
        String carolToken = carol.get("accessToken").asText();
        long aliceId = alice.get("user").get("id").asLong();
        long bobId = bob.get("user").get("id").asLong();
        long carolId = carol.get("user").get("id").asLong();

        String createBody = objectMapper.writeValueAsString(
                new CreateConversationRequest(ConversationType.PRIVATE, null, List.of(bobId)));
        MvcResult createResult = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("PRIVATE"))
                .andReturn();
        long conversationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        MvcResult duplicate = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        assertEquals(conversationId, objectMapper.readTree(duplicate.getResponse().getContentAsString())
                .get("id").asLong());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Accès refusé"));

        mockMvc.perform(get("/api/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + carolToken))
                .andExpect(status().isForbidden());

        assertThrows(ForbiddenException.class, () -> messageService.send(carolId,
                new SendMessageRequest(conversationId, "Bonjour", null)));
        assertThrows(ResourceNotFoundException.class, () -> messageService.send(bobId,
                new SendMessageRequest(conversationId + 9999, "Bonjour", null)));

        messageService.send(aliceId, new SendMessageRequest(conversationId, "Bonjour", "integration-1"));

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].senderId").value(aliceId))
                .andExpect(jsonPath("$.content[0].content").value("Bonjour"));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/members")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest(carolId))))
                .andExpect(status().isBadRequest());
    }

    private JsonNode register(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(name, email, "password123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
