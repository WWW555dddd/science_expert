package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertDimensionViewServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @InjectMocks
    private ExpertDimensionViewService service;

    @Test
    void alwaysReturnsThirtySlotsAndAttachesEvidence()
    {
        List<ExpertGraphDimension> dimensions = new ArrayList<>();
        for (int index = 1; index <= 30; index++)
        {
            ExpertGraphDimension dimension = new ExpertGraphDimension();
            dimension.setDimensionCode("D" + index);
            dimension.setDimensionName("维度" + index);
            dimensions.add(dimension);
        }
        when(platformMapper.selectGraphDimensions(12L, "DRAFT"))
                .thenReturn(dimensions);
        when(platformMapper.selectLatestDimensionEvidence(12L, "DRAFT"))
                .thenReturn(List.of(Map.of(
                        "dimensionCode", "D1",
                        "materialId", 20L,
                        "score", 80,
                        "confidence", 0.9,
                        "evidence", "材料证据",
                        "inferenceMethod", "LLM")));

        List<ExpertGraphDimension> result = service.dimensions(12L, "DRAFT");

        assertEquals(30, result.size());
        assertEquals(1, result.get(0).getEvidenceItems().size());
    }
}
