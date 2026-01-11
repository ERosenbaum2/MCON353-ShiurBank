package springContents.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Controller for serving the series page.
 * Routes numeric series IDs to the series.html page for client-side rendering.
 */
@Controller
public class SeriesPageController {

    /**
     * Routes requests to the series page for a specific series ID.
     * The regex pattern ensures only numeric IDs match, preventing conflicts with static resources.
     *
     * @param id the series ID from the URL path
     * @return the forward path to series.html
     */
    @GetMapping("/series/{id:\\d+}")
    public String seriesPage(@PathVariable("id") Long id) {
        // Forward to static series.html; the JS on that page reads the ID from the URL path.
        // The regex pattern \\d+ ensures only numeric IDs match, preventing conflicts with static resources.
        return "forward:/series.html";
    }
}