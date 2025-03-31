package com.apartment.bot;

import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/scraper")
public class ParariusScraperController {

    private ParariusScraper parariusScraper;

    public ParariusScraperController(ParariusScraper parariusScraper) {
        this.parariusScraper = parariusScraper;
    }

    @GetMapping(value = "/{action}", produces = APPLICATION_JSON_VALUE)
    public List<String> scrape(@PathVariable String action, @RequestParam(required = false) Integer id) {
        return parariusScraper.perform(action, id);
    }
}
