package com.apartment.bot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/scraper")
public class ParariusScraperController {

    private ParariusScraper parariusScraper;

    public ParariusScraperController(ParariusScraper parariusScraper) {
        this.parariusScraper = parariusScraper;
    }

    @GetMapping(value = "/{action}", produces = APPLICATION_JSON_VALUE)
    public String scrape(@PathVariable String action) {
        return parariusScraper.perform(action);
    }
}
