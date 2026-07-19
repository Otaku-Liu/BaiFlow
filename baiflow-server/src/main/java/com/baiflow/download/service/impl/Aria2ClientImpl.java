package com.baiflow.download.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baiflow.common.entity.ApiResponse.Code;
import com.baiflow.common.exception.BusinessException;
import com.baiflow.download.service.Aria2Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * aria2 JSON-RPC 客户端实现 — 通过 HTTP 与 aria2 守护进程通信。
 * <p>
 * 遵循 JSON-RPC 2.0 协议。所有方法调用返回前均会检查 RPC 响应中的 error 字段，
 * 如果 aria2 返回错误则抛出 {@code BusinessException(DOWNLOAD_ENGINE_ERROR)}。
 * <p>
 * JSON 序列化与解析统一使用阿里巴巴 fastjson。
 */
@Service
public class Aria2ClientImpl implements Aria2Client {

    private static final Logger log = LoggerFactory.getLogger(Aria2ClientImpl.class);
    private static final String JSONRPC_VERSION = "2.0";

    private final RestTemplate restTemplate;
    private final String rpcUrl;
    private final String secret;

    public Aria2ClientImpl(@Value("${baiflow.aria2.url:http://127.0.0.1:6800/jsonrpc}") String rpcUrl,
                           @Value("${baiflow.aria2.secret:}") String secret) {
        this.rpcUrl = rpcUrl;
        this.secret = secret;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String addUri(String url, String dirPath, String fileName) {
        // 构造 aria2.addUri 参数
        JSONObject options = new JSONObject();
        options.put("dir", dirPath);
        if (fileName != null && !fileName.isBlank()) {
            options.put("out", fileName);
        }

        JSONArray params = new JSONArray();
        params.add(secretToken());
        params.add(Collections.singletonList(url)); // uris 是二维数组
        params.add(options);

        JSONObject result = callRpc("aria2.addUri", params);
        return result.getString("result");
    }

    @Override
    public Map<String, Object> tellStatus(String gid) {
        JSONArray params = new JSONArray();
        params.add(secretToken());
        params.add(gid);

        JSONObject result = callRpc("aria2.tellStatus", params);
        return result.getJSONObject("result").getInnerMap();
    }

    @Override
    public void pause(String gid) {
        JSONArray params = new JSONArray();
        params.add(secretToken());
        params.add(gid);
        callRpc("aria2.pause", params);
    }

    @Override
    public void unpause(String gid) {
        JSONArray params = new JSONArray();
        params.add(secretToken());
        params.add(gid);
        callRpc("aria2.unpause", params);
    }

    @Override
    public void remove(String gid) {
        JSONArray params = new JSONArray();
        params.add(secretToken());
        params.add(gid);
        callRpc("aria2.remove", params);
    }

    @Override
    public boolean isAvailable() {
        try {
            JSONArray params = new JSONArray();
            callRpc("aria2.getVersion", params);
            return true;
        } catch (Exception e) {
            log.warn("aria2 服务不可用: {}", e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------
    // 内部方法
    // -------------------------------------------------------

    /**
     * 封装密钥 token
     */
    private String secretToken() {
        return "token:" + (secret != null ? secret : "");
    }

    /**
     * 构造并发送 JSON-RPC 请求，解析响应并处理错误。
     */
    private JSONObject callRpc(String method, JSONArray params) {
        String id = UUID.randomUUID().toString().substring(0, 8);

        JSONObject request = new JSONObject();
        request.put("jsonrpc", JSONRPC_VERSION);
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        try {
            String body = request.toJSONString();
            log.debug("aria2 RPC 请求: method={}, id={}", method, id);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(rpcUrl, HttpMethod.POST, entity, String.class);
            JSONObject root = JSON.parseObject(response.getBody());

            // 检查 JSON-RPC 错误
            if (root.containsKey("error") && root.get("error") != null) {
                JSONObject err = root.getJSONObject("error");
                String errMsg = err != null ? err.getString("message") : "aria2 RPC 错误";
                log.error("aria2 RPC 返回错误: method={}, message={}", method, errMsg);
                throw new BusinessException(Code.DOWNLOAD_ENGINE_ERROR, "下载引擎错误：" + errMsg);
            }

            return root;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("aria2 RPC 调用失败: method={}, error={}", method, e.getMessage());
            throw new BusinessException(Code.DOWNLOAD_ENGINE_ERROR,
                    "无法连接下载引擎：" + e.getMessage());
        }
    }
}
