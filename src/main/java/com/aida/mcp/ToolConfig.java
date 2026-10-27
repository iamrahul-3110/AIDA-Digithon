package com.aida.mcp;

import java.util.List;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aida.engine.ExplainEngine;
import com.aida.engine.ForecastEngine;
import com.aida.engine.QualityEngine;
import com.aida.engine.ReportEngine;
import com.aida.engine.RiskEngine;
import com.aida.engine.WhatIfEngine;

/** Exposes every {@link AidaToolSet} bean as MCP tools, and the pure engines as beans the tools inject. */
@Configuration(proxyBeanMethods = false)
public class ToolConfig {

    @Bean
    ToolCallbackProvider aidaTools(List<AidaToolSet> toolSets) {
        return MethodToolCallbackProvider.builder().toolObjects(toolSets.toArray()).build();
    }

    @Bean
    ForecastEngine forecastEngine() {
        return new ForecastEngine();
    }

    @Bean
    QualityEngine qualityEngine() {
        return new QualityEngine();
    }

    @Bean
    ExplainEngine explainEngine() {
        return new ExplainEngine();
    }

    @Bean
    WhatIfEngine whatIfEngine() {
        return new WhatIfEngine();
    }

    @Bean
    RiskEngine riskEngine() {
        return new RiskEngine();
    }

    @Bean
    ReportEngine reportEngine() {
        return new ReportEngine();
    }
}
