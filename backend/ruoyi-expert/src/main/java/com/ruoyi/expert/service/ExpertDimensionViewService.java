package com.ruoyi.expert.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 返回固定30维画像槽位并附加逐材料证据。
 */
@Service
public class ExpertDimensionViewService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    public List<ExpertGraphDimension> dimensions(Long userId, String graphStatus)
    {
        List<ExpertGraphDimension> dimensions =
                platformMapper.selectGraphDimensions(userId, graphStatus);
        String evidenceStatus = graphStatus;
        if ("DRAFT".equals(graphStatus)
                && dimensions.stream().allMatch(item ->
                        "EMPTY".equals(item.getGraphStatus())))
        {
            // 兼容升级前只保留正式分数的专家；新审核发布后会同步生成当前草稿。
            dimensions = platformMapper.selectGraphDimensions(userId, "APPROVED");
            evidenceStatus = "APPROVED";
        }
        Map<String, List<Map<String, Object>>> evidenceByDimension = new HashMap<>();
        for (Map<String, Object> evidence :
                platformMapper.selectLatestDimensionEvidence(userId, evidenceStatus))
        {
            String code = String.valueOf(evidence.get("dimensionCode"));
            Map<String, Object> item = new HashMap<>();
            item.put("evidenceId", evidence.get("evidenceId"));
            item.put("materialId", evidence.get("materialId"));
            item.put("materialCategory", evidence.get("materialCategory"));
            item.put("versionNo", evidence.get("versionNo"));
            item.put("fileName", evidence.get("fileName"));
            item.put("score", evidence.get("score"));
            item.put("confidence", evidence.get("confidence"));
            item.put("evidence", evidence.get("evidence"));
            item.put("inferenceMethod", evidence.get("inferenceMethod"));
            item.put("graphRelationId", evidence.get("graphRelationId"));
            item.put("graphEntityId", evidence.get("graphEntityId"));
            item.put("graphEvidenceId", evidence.get("graphEvidenceId"));
            item.put("profileRuleVersion", evidence.get("profileRuleVersion"));
            evidenceByDimension.computeIfAbsent(code, key -> new ArrayList<>()).add(item);
        }
        for (ExpertGraphDimension dimension : dimensions)
        {
            dimension.setEvidenceItems(
                    evidenceByDimension.getOrDefault(dimension.getDimensionCode(), List.of()));
        }
        return dimensions;
    }
}
