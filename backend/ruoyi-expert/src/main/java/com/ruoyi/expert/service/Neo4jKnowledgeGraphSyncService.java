package com.ruoyi.expert.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.config.ExpertKnowledgeGraphProperties;

/**
 * 通过 Neo4j HTTP 事务接口同步只读图谱投影，不引入驱动依赖。
 */
@Service
public class Neo4jKnowledgeGraphSyncService
{
    private static final String UPSERT_NODES = """
            UNWIND $nodes AS row
            MERGE (n:KGNode {entityId: row.entityId})
            SET n.entityType = row.entityType,
                n.externalKey = row.externalKey,
                n.canonicalName = row.canonicalName,
                n.reviewStatus = row.reviewStatus,
                n.ontologyVersion = row.ontologyVersion,
                n.propertiesJson = row.propertiesJson,
                n.evidenceCount = row.evidenceCount
            """;

    private static final String UPSERT_RELATIONS = """
            UNWIND $relations AS row
            MATCH (s:KGNode {entityId: row.sourceEntityId})
            MATCH (t:KGNode {entityId: row.targetEntityId})
            MERGE (s)-[r:KG_RELATION {relationId: row.relationId}]->(t)
            SET r.relationType = row.relationType,
                r.reviewStatus = row.reviewStatus,
                r.confidence = row.confidence,
                r.sourceKind = row.sourceKind,
                r.ontologyVersion = row.ontologyVersion,
                r.validFrom = row.validFrom,
                r.validTo = row.validTo,
                r.active = row.active,
                r.ownerExpertId = row.ownerExpertId,
                r.evidenceCount = row.evidenceCount
            """;

    private static final String DISABLE_EXPERT_RELATIONS = """
            MATCH ()-[r:KG_RELATION {ownerExpertId: $ownerExpertId}]->()
            SET r.active = false,
                r.validTo = coalesce(r.validTo, datetime())
            """;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    private ExpertKnowledgeGraphProperties properties;

    @Autowired
    private KnowledgeGraphQueryService queryService;

    public boolean isEnabled()
    {
        return properties.isEnabled()
                && StringUtils.isNotEmpty(properties.getNeo4jPassword());
    }

    public void syncEvent(Map<String, Object> event)
    {
        if (!isEnabled())
        {
            return;
        }
        String eventType = String.valueOf(event.get("eventType"));
        JSONObject payload = parsePayload(event.get("payloadJson"));
        if ("TASK_CREATED".equals(eventType))
        {
            Long taskEntityId = payload.getLong("taskEntityId");
            if (taskEntityId == null)
            {
                throw new ServiceException("任务图谱事件缺少 taskEntityId："
                        + event.get("eventId"));
            }
            syncTask(taskEntityId);
            return;
        }
        Long userId = payload.getLong("userId");
        if ("EXPERT_DELETED".equals(eventType))
        {
            deleteExpert(userId);
            return;
        }
        if (userId == null)
        {
            userId = parseLong(event.get("aggregateId"));
        }
        if (userId == null)
        {
            throw new ServiceException("知识图谱事件缺少 userId：" + event.get("eventId"));
        }
        syncExpert(userId);
    }

    public void syncExpert(Long userId)
    {
        Map<String, Object> subgraph = queryService.expertSubgraph(userId, 3);
        if (!"READY".equals(subgraph.get("status")))
        {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities =
                (List<Map<String, Object>>) subgraph.get("entities");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations =
                (List<Map<String, Object>>) subgraph.get("relations");
        execute(DISABLE_EXPERT_RELATIONS, Map.of("ownerExpertId", userId));
        execute(UPSERT_NODES, Map.of("nodes", nodeRows(entities)));
        execute(UPSERT_RELATIONS, Map.of("relations", relationRows(relations, userId)));
    }

    public void syncTask(Long taskEntityId)
    {
        Map<String, Object> subgraph = queryService.taskSubgraph(taskEntityId, 1);
        if (!"READY".equals(subgraph.get("status")))
        {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities =
                (List<Map<String, Object>>) subgraph.get("entities");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations =
                (List<Map<String, Object>>) subgraph.get("relations");
        execute(UPSERT_NODES, Map.of("nodes", nodeRows(entities)));
        execute(UPSERT_RELATIONS, Map.of("relations", relationRows(relations, null)));
    }

    private void deleteExpert(Long userId)
    {
        if (userId == null)
        {
            return;
        }
        execute("""
                MATCH ()-[r:KG_RELATION {ownerExpertId: $ownerExpertId}]->()
                DELETE r
                """, Map.of("ownerExpertId", userId));
        execute("""
                MATCH (n:KGNode {externalKey: $externalKey})
                DETACH DELETE n
                """, Map.of("externalKey", "expert:" + userId));
    }

    private List<Map<String, Object>> nodeRows(List<Map<String, Object>> entities)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> entity : entities)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("entityId", longValue(entity.get("entityId")));
            row.put("entityType", entity.get("entityType"));
            row.put("externalKey", entity.get("externalKey"));
            row.put("canonicalName", entity.get("canonicalName"));
            row.put("reviewStatus", entity.get("reviewStatus"));
            row.put("ontologyVersion", entity.get("ontologyVersion"));
            row.put("propertiesJson", JSON.toJSONString(entity.get("properties")));
            Object evidence = entity.get("evidence");
            row.put("evidenceCount", evidence instanceof List<?> list ? list.size() : 0);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> relationRows(List<Map<String, Object>> relations,
            Long ownerExpertId)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> relation : relations)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("relationId", longValue(relation.get("relationId")));
            row.put("sourceEntityId", longValue(relation.get("sourceEntityId")));
            row.put("targetEntityId", longValue(relation.get("targetEntityId")));
            row.put("relationType", relation.get("relationType"));
            row.put("reviewStatus", relation.get("reviewStatus"));
            row.put("confidence", relation.get("confidence"));
            row.put("sourceKind", relation.get("sourceKind"));
            row.put("ontologyVersion", relation.get("ontologyVersion"));
            row.put("validFrom", relation.get("validFrom"));
            row.put("validTo", relation.get("validTo"));
            row.put("active", relation.get("validTo") == null);
            row.put("ownerExpertId", ownerExpertId);
            Object evidence = relation.get("evidence");
            row.put("evidenceCount", evidence instanceof List<?> list ? list.size() : 0);
            rows.add(row);
        }
        return rows;
    }

    private void execute(String statement, Map<String, Object> parameters)
    {
        JSONObject requestBody = new JSONObject();
        JSONArray statements = new JSONArray();
        JSONObject statementBody = new JSONObject();
        statementBody.put("statement", statement);
        statementBody.put("parameters", parameters);
        statements.add(statementBody);
        requestBody.put("statements", statements);

        try
        {
            String baseUrl = properties.getNeo4jUrl().replaceAll("/+$", "");
            URI uri = URI.create(baseUrl + "/db/" + properties.getDatabase() + "/tx/commit");
            String auth = Base64.getEncoder().encodeToString(
                    (properties.getNeo4jUsername() + ":" + properties.getNeo4jPassword())
                            .getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestBody.toJSONString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2)
            {
                throw new ServiceException("Neo4j同步失败，状态码：" + response.statusCode()
                        + " " + abbreviate(response.body(), 500));
            }
            JSONObject result = JSON.parseObject(response.body());
            JSONArray errors = result == null ? null : result.getJSONArray("errors");
            if (errors != null && !errors.isEmpty())
            {
                throw new ServiceException("Neo4j同步返回错误：" + abbreviate(errors.toJSONString(), 500));
            }
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("Neo4j同步异常：" + ex.getMessage());
        }
    }

    private static JSONObject parsePayload(Object value)
    {
        if (value == null || StringUtils.isEmpty(value.toString()))
        {
            return new JSONObject();
        }
        return JSON.parseObject(value.toString());
    }

    private static Long parseLong(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private static Long longValue(Object value)
    {
        return parseLong(value);
    }

    private static String abbreviate(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
