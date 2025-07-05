package org.example.mcpclient.controllers;

import org.example.mcpclient.agents.AIAgent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class MCPController {
    private AIAgent agent;

    public MCPController(AIAgent agent) {
        this.agent = agent;
    }
    @GetMapping("/chat")
    public String askAgent(String query) {
        return agent.prompt(query);
    }
}