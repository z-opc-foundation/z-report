package com.zifang.z.report.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1 端到端链路验证 (不启动真实端口):
 * 注册内存数据源 → 注册数据集(含过滤) → 注册视图 → 整页渲染 → Book 编排发布。
 */
@SpringBootTest(classes = EndToEndFlowTest.TestApp.class)
@AutoConfigureMockMvc
class EndToEndFlowTest {

    // 排除 Spring/Druid 数据源自动配置: z-report 的 JDBC 源为自管理 Druid (z-boot 约定),
    // classpath 上有 druid+mysql 时不应激活任何容器级数据源
    @SpringBootApplication(scanBasePackages = "com.zifang.z.report",
            exclude = {org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                    com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceAutoConfigure.class})
    static class TestApp {
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void fullPipeline() throws Exception {
        // 1. 注册内存数据源 (订单明细)
        String sourceBody = "{\"def\":{\"id\":\"ds1\",\"name\":\"demo\",\"type\":\"MEMORY\"},"
                + "\"tables\":{\"orders\":["
                + "{\"city\":\"hangzhou\",\"amount\":100},"
                + "{\"city\":\"shanghai\",\"amount\":200},"
                + "{\"city\":\"hangzhou\",\"amount\":50}]}}";
        mvc.perform(post("/api/datasource/memory").contentType("application/json").content(sourceBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        // 2. 注册数据集
        String datasetBody = "{\"id\":\"ds-order\",\"name\":\"订单\",\"sourceId\":\"ds1\",\"baseTable\":\"orders\"}";
        mvc.perform(post("/api/dataset").contentType("application/json").content(datasetBody))
                .andExpect(status().isOk());

        // 3. 明细预览
        mvc.perform(post("/api/dataset/ds-order/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        // 4. 单 widget 渲染 (柱状图: 城市 → 金额求和)
        String widgetBody = "{\"id\":\"w1\",\"type\":\"BAR\",\"title\":\"城市销售额\","
                + "\"datasetId\":\"ds-order\","
                + "\"encode\":{\"x\":\"city\",\"y\":\"amount\"},\"agg\":\"SUM\"}";
        mvc.perform(post("/api/render/widget").contentType("application/json").content(widgetBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._widgetType").value("BAR"))
                .andExpect(jsonPath("$.xAxis[0]").value("hangzhou"));

        // 5. 注册视图页 + 整页渲染
        String viewBody = "{\"id\":\"view1\",\"title\":\"销售看板\","
                + "\"widgets\":[{\"id\":\"w1\",\"type\":\"BAR\",\"datasetId\":\"ds-order\","
                + "\"encode\":{\"x\":\"city\",\"y\":\"amount\"}}],"
                + "\"layout\":[{\"widgetId\":\"w1\",\"x\":0,\"y\":0,\"w\":6,\"h\":4}]}";
        mvc.perform(post("/api/view").contentType("application/json").content(viewBody))
                .andExpect(status().isOk());
        mvc.perform(get("/api/render/view/view1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.w1.xAxis[0]").value("hangzhou"));

        // 6. Book 编排 + 整体发布
        MvcResult bookResult = mvc.perform(post("/api/book")
                        .contentType("application/json").content("{\"title\":\"经营分析\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String bookId = readField(bookResult.getResponse().getContentAsString(), "bookId");

        String nodeBody = "{\"parentId\":null,\"nodeType\":\"PAGE\",\"title\":\"销售页\",\"viewId\":\"view1\"}";
        mvc.perform(post("/api/book/" + bookId + "/node").contentType("application/json").content(nodeBody))
                .andExpect(status().isOk());

        MvcResult pubResult = mvc.perform(post("/api/book/" + bookId + "/publish"))
                .andExpect(status().isOk())
                .andReturn();
        String publishId = readField(pubResult.getResponse().getContentAsString(), "publishId");
        String shareToken = readField(pubResult.getResponse().getContentAsString(), "shareToken");

        // 7. 观看侧读取快照 (不可变; M2 起必须携带分享 token)
        mvc.perform(get("/api/book/publication/" + publishId + "?token=" + shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId))
                // nodes[0] 是建书时自动生成的根 FOLDER (viewId=null), PAGE 节点在 nodes[1]
                .andExpect(jsonPath("$.nodes[1].viewId").value("view1"));
    }

    private String readField(String json, String field) {
        int keyIdx = json.indexOf("\"" + field + "\":");
        int start = json.indexOf('"', keyIdx + field.length() + 3);
        int end = json.indexOf('"', start + 1);
        return json.substring(start + 1, end);
    }
}
