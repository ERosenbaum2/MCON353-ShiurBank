package springContents.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import springContents.model.SearchResult;
import springContents.model.User;
import springContents.service.SearchService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for search operations.
 * Handles search queries for series and recordings with pagination support.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    /**
     * Constructs a new SearchController with the specified search service.
     *
     * @param searchService the SearchService for performing searches
     */
    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Performs a search query for series and recordings with pagination.
     *
     * @param query the search query string
     * @param page the page number (0-based)
     * @param pageSize the number of results per page
     * @param session the HTTP session for authentication
     * @return a response map with search results, pagination info, and success status
     * @throws RuntimeException if search execution fails
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        // Check authentication
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.put("success", false);
            response.put("message", "User not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        try {
            // Validate query
            if (query == null || query.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Search query is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Perform search
            List<SearchResult> results = searchService.search(query, user.getUserId(), page, pageSize);
            int totalResults = searchService.getTotalResults(query, user.getUserId());
            int totalPages = (int) Math.ceil((double) totalResults / pageSize);

            response.put("success", true);
            response.put("query", query);
            response.put("results", results);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("totalResults", totalResults);
            response.put("totalPages", totalPages);
            response.put("hasNextPage", page < totalPages - 1);
            response.put("hasPreviousPage", page > 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error performing search: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}