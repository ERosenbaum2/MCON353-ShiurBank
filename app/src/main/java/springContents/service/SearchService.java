package springContents.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import springContents.dao.ParticipantApprovalDAO;
import springContents.dao.SearchDAO;
import springContents.model.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final SearchDAO searchDAO;
    private final ParticipantApprovalDAO participantApprovalDAO;

    // Common stop words to filter out
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
            "to", "was", "will", "with", "the", "this", "but", "they", "have",
            "had", "what", "when", "where", "who", "which", "why", "how"
    ));

    @Autowired
    public SearchService(SearchDAO searchDAO, ParticipantApprovalDAO participantApprovalDAO) {
        this.searchDAO = searchDAO;
        this.participantApprovalDAO = participantApprovalDAO;
    }

    /**
     * Main search method that parses query and returns ranked results
     */
    public List<SearchResult> search(String query, Long userId, int page, int pageSize) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Parse the query into different categories
        ParsedQuery parsed = parseQuery(query);

        // Search both series and recordings
        List<SearchResult> seriesResults = searchDAO.searchSeries(
                parsed.keywords, parsed.rebbiNames, parsed.topicNames,
                parsed.institutionNames, userId
        );

        List<SearchResult> recordingResults = searchDAO.searchRecordings(
                parsed.keywords, parsed.rebbiNames, parsed.topicNames,
                parsed.institutionNames, userId
        );

        // Combine and score results
        List<SearchResult> allResults = new ArrayList<>();
        allResults.addAll(seriesResults);
        allResults.addAll(recordingResults);

        // Check pending applications for series results
        for (SearchResult result : allResults) {
            if ("SERIES".equals(result.getType())) {
                boolean hasPending = participantApprovalDAO.hasPendingApplication(userId, result.getId());
                result.setHasPendingApplication(hasPending);
            } else if ("RECORDING".equals(result.getType())) {
                // For recordings, check if user has pending application to the parent series
                boolean hasPending = participantApprovalDAO.hasPendingApplication(userId, result.getSeriesId());
                result.setHasPendingApplication(hasPending);
            }
        }

        // Calculate relevance scores
        for (SearchResult result : allResults) {
            int score = calculateRelevanceScore(result, parsed, query);
            result.setRelevanceScore(score);
        }

        // Get user's institutions for prioritization
        List<Long> userInstitutions = searchDAO.getUserInstitutions(userId);

        // Sort by: access > user's institutions > relevance score
        allResults.sort((a, b) -> {
            // First priority: has access
            if (a.isHasAccess() != b.isHasAccess()) {
                return a.isHasAccess() ? -1 : 1;
            }

            // Second priority: user's institutions (if we had inst_id in SearchResult)
            // For now, we can check by institution name if needed

            // Third priority: relevance score
            return Integer.compare(b.getRelevanceScore(), a.getRelevanceScore());
        });

        // Apply pagination
        int start = page * pageSize;
        int end = Math.min(start + pageSize, allResults.size());

        if (start >= allResults.size()) {
            return new ArrayList<>();
        }

        return allResults.subList(start, end);
    }

    /**
     * Get total count of results for pagination
     */
    public int getTotalResults(String query, Long userId) {
        if (query == null || query.trim().isEmpty()) {
            return 0;
        }

        ParsedQuery parsed = parseQuery(query);

        List<SearchResult> seriesResults = searchDAO.searchSeries(
                parsed.keywords, parsed.rebbiNames, parsed.topicNames,
                parsed.institutionNames, userId
        );

        List<SearchResult> recordingResults = searchDAO.searchRecordings(
                parsed.keywords, parsed.rebbiNames, parsed.topicNames,
                parsed.institutionNames, userId
        );

        return seriesResults.size() + recordingResults.size();
    }

    /**
     * Parse the search query into structured components
     */
    private ParsedQuery parseQuery(String query) {
        ParsedQuery parsed = new ParsedQuery();

        // Get all known rebbeim, topics, and institutions from database
        List<String> allRebbeim = searchDAO.getAllRebbiNames();
        List<String> allTopics = searchDAO.getAllTopicNames();
        List<String> allInstitutions = searchDAO.getAllInstitutionNames();

        String lowerQuery = query.toLowerCase();

        // Extract rebbi names
        for (String rebbi : allRebbeim) {
            if (lowerQuery.contains(rebbi)) {
                parsed.rebbiNames.add(rebbi);
            }
        }

        // Extract topic names
        for (String topic : allTopics) {
            if (lowerQuery.contains(topic.toLowerCase())) {
                parsed.topicNames.add(topic);
            }
        }

        // Extract institution names
        for (String institution : allInstitutions) {
            if (lowerQuery.contains(institution.toLowerCase())) {
                parsed.institutionNames.add(institution);
            }
        }

        // Extract keywords (non-stop words)
        String[] words = query.toLowerCase().split("\\s+");
        for (String word : words) {
            // Remove punctuation
            word = word.replaceAll("[^a-zA-Z0-9']", "");

            if (!word.isEmpty() && !STOP_WORDS.contains(word) && word.length() > 2) {
                // Don't add if already matched as rebbi/topic/institution
                boolean alreadyMatched = false;

                for (String rebbi : parsed.rebbiNames) {
                    if (rebbi.contains(word)) {
                        alreadyMatched = true;
                        break;
                    }
                }

                if (!alreadyMatched) {
                    for (String topic : parsed.topicNames) {
                        if (topic.toLowerCase().contains(word)) {
                            alreadyMatched = true;
                            break;
                        }
                    }
                }

                if (!alreadyMatched) {
                    for (String inst : parsed.institutionNames) {
                        if (inst.toLowerCase().contains(word)) {
                            alreadyMatched = true;
                            break;
                        }
                    }
                }

                if (!alreadyMatched) {
                    parsed.keywords.add(word);
                }
            }
        }

        return parsed;
    }

    /**
     * Calculate relevance score for a result
     */
    private int calculateRelevanceScore(SearchResult result, ParsedQuery parsed, String originalQuery) {
        int score = 0;
        String lowerQuery = originalQuery.toLowerCase();

        // Exact title match (highest priority)
        if (result.getTitle() != null && result.getTitle().toLowerCase().equals(lowerQuery)) {
            score += 100;
        }

        // Title contains query
        if (result.getTitle() != null && result.getTitle().toLowerCase().contains(lowerQuery)) {
            score += 50;
        }

        // Rebbi match
        if (result.getRebbiName() != null) {
            String rebbiLower = result.getRebbiName().toLowerCase();
            for (String rebbi : parsed.rebbiNames) {
                if (rebbiLower.contains(rebbi)) {
                    score += 30;
                }
            }
        }

        // Topic match
        if (result.getTopicName() != null) {
            String topicLower = result.getTopicName().toLowerCase();
            for (String topic : parsed.topicNames) {
                if (topicLower.equalsIgnoreCase(topic)) {
                    score += 30;
                }
            }
        }

        // Institution match
        if (result.getInstitutionName() != null) {
            String instLower = result.getInstitutionName().toLowerCase();
            for (String inst : parsed.institutionNames) {
                if (instLower.equalsIgnoreCase(inst)) {
                    score += 20;
                }
            }
        }

        // Keyword matches in title
        if (result.getTitle() != null) {
            String titleLower = result.getTitle().toLowerCase();
            for (String keyword : parsed.keywords) {
                if (titleLower.contains(keyword)) {
                    score += 15;
                }
            }
        }

        // Keyword matches in description
        if (result.getDescription() != null) {
            String descLower = result.getDescription().toLowerCase();
            for (String keyword : parsed.keywords) {
                if (descLower.contains(keyword)) {
                    score += 10;
                }
            }
        }

        // Multiple keyword matches (bonus for relevance)
        int keywordMatchCount = 0;
        if (result.getTitle() != null) {
            for (String keyword : parsed.keywords) {
                if (result.getTitle().toLowerCase().contains(keyword)) {
                    keywordMatchCount++;
                }
            }
        }
        if (result.getDescription() != null) {
            for (String keyword : parsed.keywords) {
                if (result.getDescription().toLowerCase().contains(keyword)) {
                    keywordMatchCount++;
                }
            }
        }
        score += keywordMatchCount * 5;

        return score;
    }

    /**
     * Inner class to hold parsed query components
     */
    private static class ParsedQuery {
        Set<String> keywords = new HashSet<>();
        Set<String> rebbiNames = new HashSet<>();
        Set<String> topicNames = new HashSet<>();
        Set<String> institutionNames = new HashSet<>();
    }
}