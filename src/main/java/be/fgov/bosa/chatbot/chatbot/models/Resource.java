package be.fgov.bosa.chatbot.chatbot.models;

import be.fgov.bosa.chatbot.chatbot.enums.PriorityEnum;
import be.fgov.bosa.chatbot.chatbot.models.commons.CustomAuditable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.UUID;
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resource extends CustomAuditable<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    private String url;
    
    @Enumerated(EnumType.STRING)
    private PriorityEnum priority;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private TrainingData trainingData;
}

