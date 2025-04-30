package be.fgov.bosa.chatbot.chatbot.models;

import be.fgov.bosa.chatbot.chatbot.enums.ConversationStatusEnum;
import be.fgov.bosa.chatbot.chatbot.models.commons.CustomAuditable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends CustomAuditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true)
    private String conversationId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private Chatbot chatbot;

    @Enumerated(EnumType.STRING)
    private ConversationStatusEnum status;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL)
    private List<Message> messages = new ArrayList<>();
    
    @Column(nullable = false)
    private OffsetDateTime startTime;

}