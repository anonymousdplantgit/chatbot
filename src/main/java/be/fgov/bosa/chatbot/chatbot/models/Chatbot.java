package be.fgov.bosa.chatbot.chatbot.models;

import be.fgov.bosa.chatbot.chatbot.enums.ChatbotStatusEnum;
import be.fgov.bosa.chatbot.chatbot.models.commons.CustomAuditable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chatbot extends CustomAuditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
    
    @Enumerated(EnumType.STRING)
    private ChatbotStatusEnum status;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;
    
    @OneToMany(mappedBy = "chatbot", cascade = CascadeType.ALL)
    private List<Conversation> conversations = new ArrayList<>();
    
    @OneToMany(mappedBy = "chatbot", cascade = CascadeType.ALL)
    private List<TrainingData> trainingData = new ArrayList<>();

}

