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
        return eventRepo.findAll().stream()
                .findFirst()
                .map(event -> "redirect:/e/" + event.getCode())
                .orElse("redirect:/login");
    }
}
