package com.ruoyi.expert.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 专家能力画像维度视图。
 */
public class ExpertGraphDimension
{
    private String dimensionCode;
    private String groupCode;
    private String groupName;
    private String dimensionName;
    private String description;
    private BigDecimal weight;
    private BigDecimal score;
    private BigDecimal confidence;
    private String evidence;
    private String graphStatus;
    private Integer graphVersion;
    private String profileKind;
    private String conflictFlag;
    private String inferenceMethod;
    private Integer sourceCount;
    private String graphRelationIds;
    private String graphEntityIds;
    private String graphEvidenceIds;
    private Integer graphPathCount;
    private String profileRuleVersion;
    private String dataStatus;
    private List<Map<String, Object>> evidenceItems;
    private String updateTime;

    public String getDimensionCode()
    {
        return dimensionCode;
    }

    public void setDimensionCode(String dimensionCode)
    {
        this.dimensionCode = dimensionCode;
    }

    public String getGroupCode()
    {
        return groupCode;
    }

    public void setGroupCode(String groupCode)
    {
        this.groupCode = groupCode;
    }

    public String getGroupName()
    {
        return groupName;
    }

    public void setGroupName(String groupName)
    {
        this.groupName = groupName;
    }

    public String getDimensionName()
    {
        return dimensionName;
    }

    public void setDimensionName(String dimensionName)
    {
        this.dimensionName = dimensionName;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public BigDecimal getWeight()
    {
        return weight;
    }

    public void setWeight(BigDecimal weight)
    {
        this.weight = weight;
    }

    public BigDecimal getScore()
    {
        return score;
    }

    public void setScore(BigDecimal score)
    {
        this.score = score;
    }

    public BigDecimal getConfidence()
    {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence)
    {
        this.confidence = confidence;
    }

    public String getEvidence()
    {
        return evidence;
    }

    public void setEvidence(String evidence)
    {
        this.evidence = evidence;
    }

    public String getGraphStatus()
    {
        return graphStatus;
    }

    public void setGraphStatus(String graphStatus)
    {
        this.graphStatus = graphStatus;
    }

    public Integer getGraphVersion()
    {
        return graphVersion;
    }

    public void setGraphVersion(Integer graphVersion)
    {
        this.graphVersion = graphVersion;
    }

    public String getProfileKind()
    {
        return profileKind;
    }

    public void setProfileKind(String profileKind)
    {
        this.profileKind = profileKind;
    }

    public String getConflictFlag()
    {
        return conflictFlag;
    }

    public void setConflictFlag(String conflictFlag)
    {
        this.conflictFlag = conflictFlag;
    }

    public String getInferenceMethod()
    {
        return inferenceMethod;
    }

    public void setInferenceMethod(String inferenceMethod)
    {
        this.inferenceMethod = inferenceMethod;
    }

    public Integer getSourceCount()
    {
        return sourceCount;
    }

    public void setSourceCount(Integer sourceCount)
    {
        this.sourceCount = sourceCount;
    }

    public String getGraphRelationIds()
    {
        return graphRelationIds;
    }

    public void setGraphRelationIds(String graphRelationIds)
    {
        this.graphRelationIds = graphRelationIds;
    }

    public String getGraphEntityIds()
    {
        return graphEntityIds;
    }

    public void setGraphEntityIds(String graphEntityIds)
    {
        this.graphEntityIds = graphEntityIds;
    }

    public String getGraphEvidenceIds()
    {
        return graphEvidenceIds;
    }

    public void setGraphEvidenceIds(String graphEvidenceIds)
    {
        this.graphEvidenceIds = graphEvidenceIds;
    }

    public Integer getGraphPathCount()
    {
        return graphPathCount;
    }

    public void setGraphPathCount(Integer graphPathCount)
    {
        this.graphPathCount = graphPathCount;
    }

    public String getProfileRuleVersion()
    {
        return profileRuleVersion;
    }

    public void setProfileRuleVersion(String profileRuleVersion)
    {
        this.profileRuleVersion = profileRuleVersion;
    }

    public String getDataStatus()
    {
        return dataStatus;
    }

    public void setDataStatus(String dataStatus)
    {
        this.dataStatus = dataStatus;
    }

    public List<Map<String, Object>> getEvidenceItems()
    {
        return evidenceItems;
    }

    public void setEvidenceItems(List<Map<String, Object>> evidenceItems)
    {
        this.evidenceItems = evidenceItems;
    }

    public String getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(String updateTime)
    {
        this.updateTime = updateTime;
    }
}
