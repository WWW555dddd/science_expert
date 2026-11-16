package com.ruoyi.expert.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;

/**
 * 版本化专家领域本体。
 */
@Service
public class KnowledgeGraphOntologyService
{
    public static final String ONTOLOGY_VERSION = "expert-domain-v2";
    public static final String RESOURCE_PATH = "knowledge-graph/ontology-v2.json";

    private volatile JSONObject ontology;

    public JSONObject getOntology()
    {
        return JSON.parseObject(load().toJSONString());
    }

    public String getVersion()
    {
        return load().getString("version");
    }

    public void requireEntityType(String entityType)
    {
        JSONObject entities = load().getJSONObject("entities");
        if (entities == null || !entities.containsKey(entityType))
        {
            throw new ServiceException("知识图谱本体不包含实体类型：" + entityType);
        }
    }

    public JSONObject requireRelationType(String relationType, String sourceType, String targetType)
    {
        JSONObject relation = load().getJSONObject("relations").getJSONObject(relationType);
        if (relation == null)
        {
            throw new ServiceException("知识图谱本体不包含关系类型：" + relationType);
        }
        JSONArray fromTypes = relation.getJSONArray("from");
        JSONArray toTypes = relation.getJSONArray("to");
        if (fromTypes == null || !fromTypes.contains(sourceType)
                || toTypes == null || !toTypes.contains(targetType))
        {
            throw new ServiceException("关系 " + relationType + " 不允许从 "
                    + sourceType + " 指向 " + targetType);
        }
        return relation;
    }

    private JSONObject load()
    {
        JSONObject cached = ontology;
        if (cached != null)
        {
            return cached;
        }
        synchronized (this)
        {
            if (ontology == null)
            {
                ontology = readOntology();
            }
            return ontology;
        }
    }

    private static JSONObject readOntology()
    {
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream())
        {
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject parsed = JSON.parseObject(json);
            if (parsed == null || !ONTOLOGY_VERSION.equals(parsed.getString("version")))
            {
                throw new ServiceException("知识图谱本体版本不匹配");
            }
            if (parsed.getJSONObject("entities") == null
                    || parsed.getJSONObject("relations") == null)
            {
                throw new ServiceException("知识图谱本体缺少 entities 或 relations 定义");
            }
            return parsed;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("加载知识图谱本体失败：" + ex.getMessage());
        }
    }
}
