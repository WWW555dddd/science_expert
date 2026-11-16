package com.ruoyi.expert.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 从 MySQL 源数据完整重建 Neo4j 只读图谱。
 */
@Service
public class KnowledgeGraphRebuildService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private KnowledgeGraphProjectionService projectionService;

    @Autowired
    private Neo4jKnowledgeGraphSyncService syncService;

    @Autowired
    private Neo4jHttpClient neo4j;

    public Map<String, Object> rebuildAll()
    {
        if (!syncService.isEnabled())
        {
            throw new IllegalStateException("Neo4j 同步未启用");
        }
        neo4j.execute("MATCH (n:KGNode) DETACH DELETE n", Map.of());
        List<Map<String, Object>> users = platformMapper.selectExpertRoleUsers();
        List<Map<String, Object>> failures = new ArrayList<>();
        int rebuilt = 0;
        for (Map<String, Object> user : users)
        {
            Long userId = numberValue(user.get("userId"));
            if (userId == null)
            {
                continue;
            }
            ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
            if (profile == null)
            {
                continue;
            }
            try
            {
                projectionService.projectExpert(userId);
                syncService.syncExpert(userId);
                rebuilt++;
            }
            catch (Exception ex)
            {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("userId", userId);
                failure.put("error", ex.getMessage());
                failures.add(failure);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        int rebuiltTasks = 0;
        for (Long taskEntityId : graphMapper.selectTaskEntityIds())
        {
            try
            {
                syncService.syncTask(taskEntityId);
                rebuiltTasks++;
            }
            catch (Exception ex)
            {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("taskEntityId", taskEntityId);
                failure.put("error", ex.getMessage());
                failures.add(failure);
            }
        }
        result.put("status", failures.isEmpty() ? "COMPLETED" : "PARTIAL");
        result.put("rebuiltExperts", rebuilt);
        result.put("rebuiltTasks", rebuiltTasks);
        result.put("failedCount", failures.size());
        result.put("failures", failures);
        return result;
    }

    private static Long numberValue(Object value)
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
}
