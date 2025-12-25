package springContents.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving HTML pages
 * Note: Protection for dashboard.html is handled client-side in dashboard.js
 * which redirects unauthenticated users to index.html
 */
@Controller
public class PageController {

    /**
     * Redirect root to index page
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/index.html";
    }
}