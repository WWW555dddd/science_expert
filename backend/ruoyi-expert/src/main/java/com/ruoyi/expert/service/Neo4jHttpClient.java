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
 * Neo4j HTTP 事务接口客户端。只允许后端执行参数化 Cypher。
 */
@Service
public class Neo4jHttpClient
{
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    private ExpertKnowledgeGraphProperties properties;

    public boolean isEnabled()
    {
        return properties.isEnabled()
                && StringUtils.isNotEmpty(properties.getNeo4jPassword());
    }

    public List<Map<String, Object>> query(String statement, Map<String, Object> parameters)
    {
        JSONObject body = request(statement, parameters);
        JSONArray results = body.getJSONArray("results");
        if (results == null || results.isEmpty())
        {
            return List.of();
        }
        JSONObject result = results.getJSONObject(0);
        JSONArray columns = result.getJSONArray("columns");
        JSONArray data = result.getJSONArray("data");
        if (columns == null || data == null)
        {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < data.size(); index++)
        {
            JSONArray values = data.getJSONObject(index).getJSONArray("row");
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 0; column < columns.size(); column++)
            {
                row.put(columns.getString(column),
                        values == null || column >= values.size() ? null : values.get(column));
            }
            rows.add(row);
        }
        return rows;
    }

    public void execute(String statement, Map<String, Object> parameters)
    {
        request(statement, parameters);
    }

    private JSONObject request(String statement, Map<String, Object> parameters)
    {
        if (!isEnabled())
        {
            throw new ServiceException("Neo4j同步未启用");
        }
        JSONObject requestBody = new JSONObject();
        JSONArray statements = new JSONArray();
        JSONObject statementBody = new JSONObject();
        statementBody.put("statement", statement);
        statementBody.put("parameters", parameters == null ? Map.of() : parameters);
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
                throw new ServiceException("Neo4j请求失败，状态码：" + response.statusCode()
                        + " " + abbreviate(response.body(), 500));
            }
            JSONObject result = JSON.parseObject(response.body());
            JSONArray errors = result == null ? null : result.getJSONArray("errors");
            if (errors != null && !errors.isEmpty())
            {
                throw new ServiceException("Neo4j返回错误：" + abbreviate(errors.toJSONString(), 500));
            }
            return result == null ? new JSONObject() : result;
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("Neo4j请求被中断：" + ex.getMessage());
        }
        catch (Exception ex)
        {
            throw new ServiceException("Neo4j请求异常：" + ex.getMessage());
        }
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
