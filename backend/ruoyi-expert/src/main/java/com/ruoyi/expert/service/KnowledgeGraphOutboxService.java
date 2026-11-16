package com.ruoyi.expert.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 将专家业务变更写入知识图谱同步 Outbox。
 */
@Service
public class KnowledgeGraphOutboxService
{
    @Autowired
    private KnowledgeGraphMapper graphMapper;

    public void enqueue(String eventType, String aggregateType, Object aggregateId,
            Map<String, Object> payload)
    {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("aggregateType", aggregateType);
        event.put("aggregateId", String.valueOf(aggregateId));
        event.put("payloadJson", JSON.toJSONString(payload == null ? Map.of() : payload));
        graphMapper.insertOutboxEvent(event);
    }
}
