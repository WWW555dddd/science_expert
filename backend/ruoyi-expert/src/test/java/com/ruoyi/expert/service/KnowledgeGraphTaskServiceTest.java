package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphTaskServiceTest
{
    @Mock
    private KnowledgeGraphMapper graphMapper;

    @Mock
    private KnowledgeGraphOutboxService outboxService;

    @Spy
    private KnowledgeGraphOntologyService ontologyService;

    @InjectMocks
    private KnowledgeGraphTaskService taskService;

    @Test
    void createsTaskAndRequiredCapabilityEdges()
    {
        Map<String, Long> ids = new ConcurrentHashMap<>();
        AtomicLong nextId = new AtomicLong(60);
        when(graphMapper.selectEntityIdByExternalKey(anyString(), anyString()))
                .thenAnswer(invocation -> ids.computeIfAbsent(
                        invocation.getArgument(0) + "|" + invocation.getArgument(1),
                        ignored -> nextId.incrementAndGet()));
        when(graphMapper.selectEntityIdByExternalKey("Task", "task:run-1"))
                .thenReturn(70L);
        when(graphMapper.selectRelationId(anyLong(), anyString(), anyLong()))
                .thenReturn(80L);
        Map<String, Object> result = taskService.createTask(
                "run-1",
                "寻找南昌新能源储能事故技术鉴定专家",
                "2026-11-20",
                "南昌",
                "ONSITE",
                "新能源",
                "技术鉴定",
                List.of("储能安全"));

        assertEquals(70L, result.get("taskEntityId"));
        assertEquals(2, result.get("requiredCapabilityCount"));
        verify(graphMapper, times(4)).upsertEntity(anyMap());
        verify(graphMapper, times(3)).upsertRelation(anyMap());
        verify(graphMapper, times(3)).upsertEvidence(anyMap());
        verify(outboxService).enqueue(
                org.mockito.ArgumentMatchers.eq("TASK_CREATED"),
                org.mockito.ArgumentMatchers.eq("TASK"),
                org.mockito.ArgumentMatchers.eq(70L),
                anyMap());
    }
}
