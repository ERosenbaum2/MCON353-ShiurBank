package springContents.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class SeriesPageController {

    @GetMapping("/series/{id:\\d+}")
    public String seriesPage(@PathVariable("id") Long id) {
        // Forward to static series.html; the JS on that page reads the ID from the URL path.
        // The regex pattern \\d+ ensures only numeric IDs match, preventing conflicts with static resources.
        return "forward:/series.html";
    }
}