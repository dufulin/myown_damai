package com.myown.damai.program.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myown.damai.program.dto.ProgramDetailResponse;
import com.myown.damai.program.dto.ProgramResponse;
import com.myown.damai.program.dto.ProgramSearchRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Uses Elasticsearch HTTP APIs to store and read program detail documents.
 */
@Component
@ConditionalOnProperty(value = "damai.search.es-enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchProgramSearchGateway implements ProgramSearchGateway {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchProgramSearchGateway.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUri;
    private final String indexName;
    private final String authorizationHeader;
    private final Duration requestTimeout;

    /**
     * Creates the Elasticsearch gateway from application search settings.
     */
    public ElasticsearchProgramSearchGateway(
            ObjectMapper objectMapper,
            @Value("${damai.search.uri:http://localhost:9200}") String baseUri,
            @Value("${damai.search.index-name:damai_program_detail}") String indexName,
            @Value("${damai.search.username:}") String username,
            @Value("${damai.search.password:}") String password,
            @Value("${damai.search.connect-timeout-seconds:2}") long connectTimeoutSeconds,
            @Value("${damai.search.request-timeout-seconds:5}") long requestTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.baseUri = stripTrailingSlash(baseUri);
        this.indexName = indexName;
        this.authorizationHeader = buildAuthorizationHeader(username, password);
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    /**
     * Creates the program detail index with a mapping only when it does not already exist.
     */
    @Override
    public boolean createProgramDetailIndexIfAbsent() {
        if (programDetailIndexExists()) {
            LOGGER.info("program detail es index already exists, indexName={}", indexName);
            return false;
        }
        String mapping = """
                {
                  "mappings": {
                    "properties": {
                      "programId": { "type": "long" },
                      "minTicketPrice": { "type": "scaled_float", "scaling_factor": 100 },
                      "maxTicketPrice": { "type": "scaled_float", "scaling_factor": 100 },
                      "programDetail": {
                        "properties": {
                          "program": {
                            "properties": {
                              "id": { "type": "long" },
                              "title": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                              "areaId": { "type": "long" },
                              "programCategoryId": { "type": "long" },
                              "parentProgramCategoryId": { "type": "long" },
                              "highHeat": { "type": "integer" },
                              "issueTime": { "type": "date" }
                            }
                          },
                          "detail": { "type": "text" },
                          "showTimes": {
                            "type": "nested",
                            "properties": {
                              "showTime": { "type": "date" },
                              "showDayTime": { "type": "date" },
                              "areaId": { "type": "long" }
                            }
                          },
                          "ticketCategories": {
                            "type": "nested",
                            "properties": {
                              "price": { "type": "scaled_float", "scaling_factor": 100 },
                              "remainNumber": { "type": "long" }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;
        HttpResponse<String> response = send("PUT", "/" + encodedIndexName(), mapping);
        if (response.statusCode() == 200) {
            LOGGER.info("program detail es index created, indexName={}", indexName);
            return true;
        }
        if (response.statusCode() == 400 && response.body().contains("resource_already_exists_exception")) {
            LOGGER.info("program detail es index already exists, indexName={}", indexName);
            return false;
        }
        LOGGER.warn("program detail es index ensure failed, indexName={}, status={}, body={}", indexName, response.statusCode(), response.body());
        return false;
    }

    /**
     * Writes one program detail document into Elasticsearch.
     */
    @Override
    public void saveProgramDetail(ProgramSearchDocument document) {
        try {
            String body = objectMapper.writeValueAsString(document);
            HttpResponse<String> httpResponse = send("PUT", "/" + encodedIndexName() + "/_doc/" + document.programId(), body);
            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                LOGGER.info("program detail es document saved, programId={}", document.programId());
                return;
            }
            LOGGER.warn("program detail es document save failed, programId={}, status={}, body={}", document.programId(), httpResponse.statusCode(), httpResponse.body());
        } catch (IOException exception) {
            LOGGER.warn("program detail es document serialization failed, programId={}", document.programId(), exception);
        }
    }

    /**
     * Reads one program detail document from Elasticsearch.
     */
    @Override
    public Optional<ProgramDetailResponse> findProgramDetail(Long programId) {
        HttpResponse<String> response = send("GET", "/" + encodedIndexName() + "/_doc/" + programId, null);
        if (response.statusCode() == 404) {
            LOGGER.info("program detail es miss, programId={}", programId);
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            LOGGER.warn("program detail es query failed, programId={}, status={}, body={}", programId, response.statusCode(), response.body());
            return Optional.empty();
        }
        try {
            JsonNode source = objectMapper.readTree(response.body()).path("_source");
            ProgramDetailResponse detail = readProgramDetailSource(source);
            LOGGER.info("program detail es hit, programId={}", programId);
            return Optional.of(detail);
        } catch (IOException exception) {
            LOGGER.warn("program detail es response parse failed, programId={}", programId, exception);
            return Optional.empty();
        }
    }

    /**
     * Searches program summary documents in Elasticsearch.
     */
    @Override
    public Optional<List<ProgramResponse>> searchPrograms(ProgramSearchRequest request) {
        try {
            String body = objectMapper.writeValueAsString(buildSearchBody(request));
            HttpResponse<String> response = send("POST", "/" + encodedIndexName() + "/_search", body);
            if (response.statusCode() == 404) {
                LOGGER.warn("program es search skipped because index is missing, indexName={}", indexName);
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                LOGGER.warn("program es search failed, status={}, body={}", response.statusCode(), response.body());
                return Optional.empty();
            }
            return Optional.of(readSearchPrograms(response.body()));
        } catch (IOException exception) {
            LOGGER.warn("program es search serialization or parse failed", exception);
            return Optional.empty();
        }
    }

    /**
     * Checks whether the program detail index already exists.
     */
    private boolean programDetailIndexExists() {
        HttpResponse<String> response = send("HEAD", "/" + encodedIndexName(), null);
        if (response.statusCode() == 200) {
            return true;
        }
        if (response.statusCode() == 404) {
            return false;
        }
        LOGGER.warn("program detail es index existence check failed, indexName={}, status={}", indexName, response.statusCode());
        return false;
    }

    /**
     * Reads a program detail from either the new search document shape or the old direct detail shape.
     */
    private ProgramDetailResponse readProgramDetailSource(JsonNode source) throws IOException {
        JsonNode programDetail = source.path("programDetail");
        if (!programDetail.isMissingNode() && !programDetail.isNull()) {
            return objectMapper.treeToValue(programDetail, ProgramDetailResponse.class);
        }
        return objectMapper.treeToValue(source, ProgramDetailResponse.class);
    }

    /**
     * Builds an Elasticsearch search body from normalized filters.
     */
    private ObjectNode buildSearchBody(ProgramSearchRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("from", (request.pageNumber() - 1) * request.pageSize());
        root.put("size", request.pageSize());

        ObjectNode bool = objectMapper.createObjectNode();
        ArrayNode must = bool.putArray("must");
        ArrayNode filter = bool.putArray("filter");
        appendKeywordQuery(must, request.keyword());
        appendCompatibleTermFilter(filter, "programDetail.program.areaId", "program.areaId", request.areaId());
        appendCompatibleTermFilter(filter, "programDetail.program.programCategoryId", "program.programCategoryId", request.programCategoryId());
        appendShowTimeRangeFilter(filter, request);
        if (must.isEmpty()) {
            ObjectNode matchAll = objectMapper.createObjectNode();
            matchAll.putObject("match_all");
            must.add(matchAll);
        }

        root.putObject("query").set("bool", bool);
        appendSort(root, request.type());
        return root;
    }

    /**
     * Appends keyword multi-match clauses for smart text search.
     */
    private void appendKeywordQuery(ArrayNode must, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        ObjectNode multiMatch = objectMapper.createObjectNode();
        ObjectNode body = multiMatch.putObject("multi_match");
        body.put("query", keyword.trim());
        body.put("type", "best_fields");
        ArrayNode fields = body.putArray("fields");
        fields.add("programDetail.program.title^4");
        fields.add("programDetail.program.actor^2");
        fields.add("programDetail.program.place^2");
        fields.add("programDetail.detail");
        fields.add("program.title^4");
        fields.add("program.actor^2");
        fields.add("program.place^2");
        fields.add("detail");
        must.add(multiMatch);
    }

    /**
     * Appends a term filter that can match both new and old ES document shapes.
     */
    private void appendCompatibleTermFilter(ArrayNode filter, String newField, String oldField, Long value) {
        if (value == null) {
            return;
        }
        ObjectNode bool = objectMapper.createObjectNode();
        ObjectNode boolBody = bool.putObject("bool");
        ArrayNode should = boolBody.putArray("should");
        should.add(termQuery(newField, value));
        should.add(termQuery(oldField, value));
        boolBody.put("minimum_should_match", 1);
        filter.add(bool);
    }

    /**
     * Appends a nested show-time range filter when the request includes a time window.
     */
    private void appendShowTimeRangeFilter(ArrayNode filter, ProgramSearchRequest request) {
        if (request.startTime() == null || request.endTime() == null) {
            return;
        }
        ObjectNode bool = objectMapper.createObjectNode();
        ObjectNode boolBody = bool.putObject("bool");
        ArrayNode should = boolBody.putArray("should");
        should.add(nestedRangeQuery("programDetail.showTimes", "programDetail.showTimes.showTime", request));
        should.add(nestedRangeQuery("showTimes", "showTimes.showTime", request));
        boolBody.put("minimum_should_match", 1);
        filter.add(bool);
    }

    /**
     * Appends sort settings for the requested search type.
     */
    private void appendSort(ObjectNode root, Integer type) {
        int sortType = type == null ? 1 : type;
        if (sortType == 1) {
            return;
        }
        ArrayNode sort = root.putArray("sort");
        if (sortType == 2) {
            sort.add(sortObject("programDetail.program.highHeat", "desc", "integer"));
            sort.add(sortObject("program.highHeat", "desc", "integer"));
            sort.add(sortObject("minTicketPrice", "asc", "double"));
            return;
        }
        if (sortType == 3) {
            sort.add(nestedSortObject("programDetail.showTimes.showTime", "programDetail.showTimes"));
            sort.add(nestedSortObject("showTimes.showTime", "showTimes"));
            return;
        }
        if (sortType == 4) {
            sort.add(sortObject("programDetail.program.issueTime", "desc", "date"));
            sort.add(sortObject("program.issueTime", "desc", "date"));
        }
    }

    /**
     * Builds one simple field sort object.
     */
    private ObjectNode sortObject(String field, String order, String unmappedType) {
        ObjectNode sort = objectMapper.createObjectNode();
        ObjectNode body = sort.putObject(field);
        body.put("order", order);
        body.put("unmapped_type", unmappedType);
        return sort;
    }

    /**
     * Builds one simple term query.
     */
    private ObjectNode termQuery(String field, Long value) {
        ObjectNode term = objectMapper.createObjectNode();
        term.putObject("term").put(field, value);
        return term;
    }

    /**
     * Builds one nested show-time range query and ignores missing nested mappings.
     */
    private ObjectNode nestedRangeQuery(String path, String field, ProgramSearchRequest request) {
        ObjectNode nested = objectMapper.createObjectNode();
        ObjectNode nestedBody = nested.putObject("nested");
        nestedBody.put("path", path);
        nestedBody.put("ignore_unmapped", true);
        ObjectNode range = nestedBody.putObject("query")
                .putObject("range")
                .putObject(field);
        range.put("gte", request.startTime().toString());
        range.put("lt", request.endTime().toString());
        return nested;
    }

    /**
     * Builds one nested show-time sort and ignores missing nested mappings.
     */
    private ObjectNode nestedSortObject(String field, String path) {
        ObjectNode showTimeSort = objectMapper.createObjectNode();
        ObjectNode showTimeBody = showTimeSort.putObject(field);
        showTimeBody.put("order", "asc");
        showTimeBody.put("mode", "min");
        showTimeBody.put("unmapped_type", "date");
        ObjectNode nested = showTimeBody.putObject("nested");
        nested.put("path", path);
        nested.put("ignore_unmapped", true);
        return showTimeSort;
    }

    /**
     * Converts Elasticsearch search hits to program summary responses.
     */
    private List<ProgramResponse> readSearchPrograms(String body) throws IOException {
        JsonNode hits = objectMapper.readTree(body).path("hits").path("hits");
        List<ProgramResponse> programs = new ArrayList<>();
        for (JsonNode hit : hits) {
            ProgramDetailResponse detail = readProgramDetailSource(hit.path("_source"));
            programs.add(detail.program());
        }
        return programs;
    }

    /**
     * Sends one HTTP request to Elasticsearch and converts transport errors to warning-only responses.
     */
    private HttpResponse<String> send(String method, String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUri + path))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json");
            if (StringUtils.hasText(authorizationHeader)) {
                builder.header("Authorization", authorizationHeader);
            }
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            return httpClient.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn("program detail es request failed, method={}, path={}", method, path, exception);
            return new FailedHttpResponse();
        }
    }

    /**
     * Builds a Basic authorization header when username and password are configured.
     */
    private String buildAuthorizationHeader(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return null;
        }
        String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Removes the trailing slash from the Elasticsearch base URI.
     */
    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * URL-encodes the configured index name.
     */
    private String encodedIndexName() {
        return URLEncoder.encode(indexName, StandardCharsets.UTF_8);
    }

    /**
     * Represents a failed Elasticsearch transport response.
     */
    private static final class FailedHttpResponse implements HttpResponse<String> {

        /**
         * Returns an empty body for failed transport calls.
         */
        @Override
        public String body() {
            return "";
        }

        /**
         * Returns service-unavailable for failed transport calls.
         */
        @Override
        public int statusCode() {
            return 503;
        }

        /**
         * Returns the request as unavailable for failed transport calls.
         */
        @Override
        public HttpRequest request() {
            return null;
        }

        /**
         * Returns the previous response as unavailable for failed transport calls.
         */
        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        /**
         * Returns empty headers for failed transport calls.
         */
        @Override
        public java.net.http.HttpHeaders headers() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (key, value) -> true);
        }

        /**
         * Returns the URI as unavailable for failed transport calls.
         */
        @Override
        public URI uri() {
            return null;
        }

        /**
         * Returns the HTTP version for failed transport calls.
         */
        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        /**
         * Returns the SSL session as unavailable for failed transport calls.
         */
        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}
