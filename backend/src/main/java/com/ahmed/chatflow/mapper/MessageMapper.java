package com.ahmed.chatflow.mapper;

import com.ahmed.chatflow.dto.MessageDto;
import com.ahmed.chatflow.entity.Message;
import com.ahmed.chatflow.entity.MessageReceipt;
import com.ahmed.chatflow.entity.ReceiptStatus;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageDto toDto(Message message) {
        if (message == null) {
            return null;
        }
        return new MessageDto(message.getId(), message.getConversation().getId(), message.getSender().getId(),
                message.getSender().getName(), message.getContent(), message.getCreatedAt(), statusOf(message),
                message.getClientId());
    }

    private String statusOf(Message message) {
        boolean delivered = false;
        for (MessageReceipt receipt : message.getReceipts()) {
            if (receipt.getStatus() == ReceiptStatus.READ) {
                return "READ";
            }
            if (receipt.getStatus() == ReceiptStatus.DELIVERED) {
                delivered = true;
            }
        }
        return delivered ? "DELIVERED" : "SENT";
    }
}
