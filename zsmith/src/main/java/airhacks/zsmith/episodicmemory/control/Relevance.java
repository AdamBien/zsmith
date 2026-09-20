package airhacks.zsmith.episodicmemory.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import airhacks.zsmith.episodicmemory.entity.Episode;

/// Ranks memories against a query the way a search engine ranks documents: a term
/// carried by few memories weighs more than one carried by many, and each further
/// occurrence of a term within one memory counts for less than the occurrence before
/// it. The customary saturation and length-normalisation constants of BM25.
///
/// Nothing is indexed, persisted or invalidated: the corpus is what the run already
/// holds, and scoring a few hundred short records outright is cheaper than keeping an
/// index correct across every memory an agent adds.
public interface Relevance {

    /// How quickly repeated occurrences of a term stop adding to a score.
    double SATURATION = 1.2;

    /// How far a memory's length discounts its score, between 0 and 1.
    double LENGTH_INFLUENCE = 0.75;

    /// Terms are runs of letters and digits, so punctuation never becomes part of one.
    String SEPARATORS = "[^\\p{IsAlphabetic}\\p{IsDigit}]+";

    record Scored(Episode episode, double score) {
    }

    /// The searchable terms of a text, lowercased — an empty list for a text that
    /// carries none, which is how a caller tells an empty query from one that simply
    /// found nothing.
    static List<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase().split(SEPARATORS))
                .filter(term -> !term.isEmpty())
                .toList();
    }

    /// The memories the query is about, most relevant first and at most `limit` of
    /// them. Equally relevant memories are answered newest first, so a result is
    /// reproducible and the fresher of two equally good memories leads.
    static List<Episode> rank(List<Episode> memories, String query, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return scored(memories, query).stream()
                .limit(limit)
                .map(Scored::episode)
                .toList();
    }

    /// The same memories carrying the score each was ranked by. Ordering is all a
    /// recall needs, but a caller weighing one score against another — how much a
    /// second occurrence of a term added, say — needs the numbers themselves.
    static List<Scored> scored(List<Episode> memories, String query) {
        var queryTerms = terms(query);
        if (queryTerms.isEmpty() || memories.isEmpty()) {
            return List.of();
        }
        var corpus = memories.stream().map(episode -> terms(episode.content())).toList();
        var averageLength = corpus.stream().mapToInt(List::size).average().orElse(0);
        var rarity = rarityOf(queryTerms, corpus);
        var scored = new ArrayList<Scored>();
        for (var index = 0; index < memories.size(); index++) {
            var score = score(queryTerms, corpus.get(index), rarity, averageLength);
            if (score > 0) {
                scored.add(new Scored(memories.get(index), score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(entry -> entry.episode().timestamp(), Comparator.reverseOrder()));
        return List.copyOf(scored);
    }

    /// Weighed once per query rather than once per memory: rarity is a property of
    /// the whole corpus, and recomputing it per memory would square the work.
    private static Map<String, Double> rarityOf(List<String> queryTerms, List<List<String>> corpus) {
        var rarity = new HashMap<String, Double>();
        for (var term : queryTerms) {
            var carrying = corpus.stream().filter(memory -> occurrences(memory, term) > 0).count();
            rarity.put(term, inverseDocumentFrequency(corpus.size(), carrying));
        }
        return rarity;
    }

    private static double score(List<String> queryTerms, List<String> memory, Map<String, Double> rarity,
            double averageLength) {
        var score = 0d;
        for (var term : queryTerms) {
            var occurrences = occurrences(memory, term);
            if (occurrences == 0) {
                continue;
            }
            score += rarity.get(term) * saturated(occurrences, memory.size(), averageLength);
        }
        return score;
    }

    /// Stays positive however common a term is, so carrying a query term can only
    /// ever raise a memory's rank, never lower it.
    private static double inverseDocumentFrequency(int corpusSize, long carrying) {
        return Math.log(1 + (corpusSize - carrying + 0.5) / (carrying + 0.5));
    }

    /// Damps the count so that a memory repeating a word is not held to be
    /// proportionally more about it, and discounts a long memory, which carries any
    /// given term more often for no better reason than its length.
    private static double saturated(int occurrences, int memoryLength, double averageLength) {
        var length = averageLength == 0
                ? 1
                : 1 - LENGTH_INFLUENCE + LENGTH_INFLUENCE * memoryLength / averageLength;
        return occurrences * (SATURATION + 1) / (occurrences + SATURATION * length);
    }

    /// A term matches any word beginning with it, so `build` finds `builds` and
    /// `builder` without carrying a stemmer.
    private static int occurrences(List<String> words, String term) {
        return (int) words.stream().filter(word -> word.startsWith(term)).count();
    }
}
