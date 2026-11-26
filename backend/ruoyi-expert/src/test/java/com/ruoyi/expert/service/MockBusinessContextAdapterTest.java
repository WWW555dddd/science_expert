package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertBusinessContextSnapshot;
import com.ruoyi.expert.mapper.ExpertOpenApiMapper;

@ExtendWith(MockitoExtension.class)
class MockBusinessContextAdapterTest
{
    @Mock
    private ExpertOpenApiMapper openApiMapper;

    @InjectMocks
    private MockBusinessContextAdapter adapter;

    @Test
    void returnsUnknownWhenNoSnapshotCoversTheRequest()
    {
        when(openApiMapper.selectOverlappingBusinessContexts(eq(12L),
                anyString(), anyString())).thenReturn(List.of());

        BusinessContextPort.BusinessContextResult result =
                adapter.inspect(query("REMOTE"));

        assertEquals("UNKNOWN", result.occupancyStatus());
        assertEquals("UNKNOWN", result.conflictStatus());
    }

    @Test
    void filtersSnapshotsThatDoNotMatchServiceMode()
    {
        ExpertBusinessContextSnapshot snapshot = snapshot("ONSITE", "FREE", "NONE");
        when(openApiMapper.selectOverlappingBusinessContexts(eq(12L),
                anyString(), anyString())).thenReturn(List.of(snapshot));

        BusinessContextPort.BusinessContextResult result =
                adapter.inspect(query("REMOTE"));

        assertEquals("UNKNOWN", result.occupancyStatus());
    }

    @Test
    void occupiedSnapshotWinsWhenFreeAndOccupiedRangesOverlap()
    {
        when(openApiMapper.selectOverlappingBusinessContexts(eq(12L),
                anyString(), anyString())).thenReturn(List.of(
                        snapshot("REMOTE", "FREE", "NONE"),
                        snapshot("REMOTE", "OCCUPIED", "POTENTIAL")));

        BusinessContextPort.BusinessContextResult result =
                adapter.inspect(query("REMOTE"));

        assertEquals("OCCUPIED", result.occupancyStatus());
        assertEquals("POTENTIAL", result.conflictStatus());
        assertTrue(result.source().contains("MOCK"));
    }

    @Test
    void keepsFreeSnapshotForCurrentTask()
    {
        ExpertBusinessContextSnapshot free = snapshot("REMOTE", "FREE", "NONE");
        free.setExternalTaskId("TASK-001");
        when(openApiMapper.selectOverlappingBusinessContexts(eq(12L),
                anyString(), anyString())).thenReturn(List.of(free));

        BusinessContextPort.BusinessContextResult result =
                adapter.inspect(query("REMOTE"));

        assertEquals("FREE", result.occupancyStatus());
        assertEquals("NONE", result.conflictStatus());
        assertTrue(result.taskRefs().contains("TASK-001"));
    }

    @Test
    void ignoresCurrentTaskOccupiedSnapshotButKeepsItsFreeBaseline()
    {
        ExpertBusinessContextSnapshot free = snapshot("REMOTE", "FREE", "NONE");
        free.setExternalTaskId("TASK-001");
        ExpertBusinessContextSnapshot ownOccupied =
                snapshot("REMOTE", "OCCUPIED", "NONE");
        ownOccupied.setExternalTaskId("TASK-001");
        when(openApiMapper.selectOverlappingBusinessContexts(eq(12L),
                anyString(), anyString())).thenReturn(List.of(free, ownOccupied));

        BusinessContextPort.BusinessContextResult result =
                adapter.inspect(query("REMOTE"));

        assertEquals("FREE", result.occupancyStatus());
        assertEquals("NONE", result.conflictStatus());
    }

    @Test
    void blocksWhenAnotherTaskOwnsTheSameTimeRange()
    {
        ExpertBusinessContextSnapshot free = snapshot("REMOTE", "FREE", "NONE");
        free.setExternalTaskId("TASK-001");
        ExpertBusinessContextSnapshot otherOccupied =
                snapshot("REMOTE", "OCCUPIED", "POTENTIAL");
        otherOccupied.setExternalTaskId("TASK-OTHER");
        when(openApiMapper.selectOverlappingBusinessContexts(eq(12L),
                anyString(), anyString())).thenReturn(List.of(free, otherOccupied));

        BusinessContextPort.BusinessContextResult result =
                adapter.inspect(query("REMOTE"));

        assertEquals("OCCUPIED", result.occupancyStatus());
        assertEquals("POTENTIAL", result.conflictStatus());
        assertTrue(result.taskRefs().contains("TASK-OTHER"));
    }

    private static BusinessContextPort.BusinessContextQuery query(String mode)
    {
        return new BusinessContextPort.BusinessContextQuery(
                12L, "TASK-001",
                LocalDateTime.of(2026, 11, 26, 9, 0),
                LocalDateTime.of(2026, 11, 26, 12, 0),
                mode, null, null);
    }

    private static ExpertBusinessContextSnapshot snapshot(String mode,
            String occupancyStatus, String conflictStatus)
    {
        ExpertBusinessContextSnapshot snapshot = new ExpertBusinessContextSnapshot();
        snapshot.setServiceMode(mode);
        snapshot.setOccupancyStatus(occupancyStatus);
        snapshot.setConflictStatus(conflictStatus);
        snapshot.setSourceKind("MOCK");
        snapshot.setSourceSystem("mock-dispatch");
        snapshot.setVerifiedAt("2026-11-19 10:00:00");
        return snapshot;
    }
}
