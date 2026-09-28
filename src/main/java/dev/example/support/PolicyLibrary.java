package dev.example.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * The refund policy, one passage per section, each with a source id such as
 * "refunds#window", so an answer can cite a passage and the citation can be
 * checked. Embeddings are computed locally; nothing about the policy leaves the
 * machine.
 */
@Component
public class PolicyLibrary implements ApplicationRunner {

    public record Passage(String source, String text, double score) {}

    private final VectorStore store;
    private final String[] locations;

    // Experiment A adds a planted passage here; the application alone loads only
    // its own policy.
    public PolicyLibrary(
            VectorStore store,
            @Value("${support.policy.locations:classpath:policy/*.md}")
            String[] locations) {
        this.store = store;
        this.locations = locations;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        var docs = new ArrayList<Document>();
        var files = new ArrayList<Resource>();
        var resolver = new PathMatchingResourcePatternResolver();
        for (var location : locations) {
            files.addAll(List.of(resolver.getResources(location)));
        }
        for (Resource file : files) {
            var name = file.getFilename().replace(".md", "");
            var text = file.getContentAsString(StandardCharsets.UTF_8);
            for (var section : text.split("\n## ")) {
                if (section.startsWith("# ")) continue;
                var heading = section.lines().findFirst().orElseThrow().trim();
                var body = section.substring(section.indexOf('\n') + 1).trim();
                var source = name + "#" + heading;
                // pgvector ids are UUIDs; a name-based one keeps re-ingestion
                // idempotent.
                var sourceBytes = source.getBytes(StandardCharsets.UTF_8);
                var id = UUID.nameUUIDFromBytes(sourceBytes).toString();
                docs.add(new Document(id, body, Map.of("source", source)));
            }
        }
        store.delete(docs.stream().map(Document::getId).toList());
        store.add(docs);
    }

    public List<Passage> search(String question) {
        var request = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.3)
                .build();
        return store.similaritySearch(request)
                .stream()
                .map(d -> new Passage(
                        (String) d.getMetadata().get("source"),
                        d.getText(),
                        d.getScore()))
                .toList();
    }
}
