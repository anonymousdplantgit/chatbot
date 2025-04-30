package be.fgov.bosa.chatbot.chatbot.api;

import be.fgov.bosa.chatbot.chatbot.resources.AllResources;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {


    @GetMapping()
    public ResponseEntity<AllResources> getInfo() {
        return ResponseEntity.ok(AllResources.builder().build());
    }


}
