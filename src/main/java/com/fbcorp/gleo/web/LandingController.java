package com.fbcorp.gleo.web;

import com.fbcorp.gleo.repo.EventRepo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

    private final EventRepo eventRepo;

    public LandingController(EventRepo eventRepo) {
        this.eventRepo = eventRepo;
    }

    @GetMapping("/")
    public String root(){
        return eventRepo.findByCode("G2025")
                .map(event -> "redirect:/e/G2025")
                .orElse("Event not found"); // never redirect to login, just show error if no event
    }
}
