package org.example.mcpclient.controllers;

import org.example.mcpclient.agents.AIAgent;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@CrossOrigin("*")
public class ChatController {
    private final AIAgent agent;

    public ChatController(AIAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/")
    public String chatPage() {
        return "chat";
    }

    @PostMapping("/chat/send")
    @ResponseBody
    public String sendMessage(@RequestParam String message) {
        return agent.prompt(message);
    }
}
