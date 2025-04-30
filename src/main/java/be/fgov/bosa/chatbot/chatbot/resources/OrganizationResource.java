package be.fgov.bosa.chatbot.chatbot.resources;

import lombok.*;

import java.util.UUID;
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class OrganizationResource {
    private UUID id;
    private String name;

}