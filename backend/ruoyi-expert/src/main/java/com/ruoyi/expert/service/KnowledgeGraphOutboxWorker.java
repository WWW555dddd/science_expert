package com.ruoyi.expert.service;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.ruoyi.expert.config.ExpertKnowledgeGraphProperties;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 将 MySQL Outbox 幂等同步到 Neo4j 只读投影。
 */
@Component
public class KnowledgeGraphOutboxWorker
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphOutboxWorker.class);

    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private ExpertKnowledgeGraphProperties properties;

    @Autowired
    private Neo4jKnowledgeGraphSyncService syncService;

    @Scheduled(fixedDelayString = "${expert.knowledge-graph.sync-delay-ms:5000}")
    public void syncPendingEvents()
    {
        if (!syncService.isEnabled())
        {
            return;
        }
        graphMapper.recoverStaleOutbox();
        List<Map<String, Object>> events =
                graphMapper.selectPendingOutbox(Math.max(1, properties.getBatchSize()));
        for (Map<String, Object> event : events)
        {
            String eventId = String.valueOf(event.get("eventId"));
            if (graphMapper.claimOutboxEvent(eventId) == 0)
            {
                continue;
            }
            try
            {
                syncService.syncEvent(event);
                graphMapper.markOutboxDone(eventId);
            }
            catch (Exception ex)
            {
                log.warn("知识图谱 Outbox 同步失败，eventId={}，error={}",
                        eventId, ex.getMessage());
                graphMapper.markOutboxRetry(eventId,
                        ex.getMessage() == null ? "unknown" :
                                ex.getMessage().substring(0, Math.min(950, ex.getMessage().length())));
            }
        }
    }
}
