package com.baiflow.common.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL 计时：每条语句执行完，在 MyBatis 自己那几行 SQL 内容
 * （{@code Preparing} / {@code Parameters} / {@code Total}）之后另起一行输出
 * {@code ==> 执行时间：xxms}，与 MyBatis 的 {@code ==>  Preparing:} 同一套箭头。
 *
 * <p>方法名不写在消息里 —— 左边的 logger 列就是它。
 *
 * <p><b>logger 名取 statement id</b>（{@code com.baiflow.file.mapper.BfFileItemMapper.selectById}），
 * 与 MyBatis 打 SQL 内容用的是<b>同一个 logger</b>：这样那一列与相邻三行完全对齐，且一份
 * {@code logback-spring.xml} 同时管住内容与计时（各 mapper 包分别开了 DEBUG）。
 * 若用本类自己的 logger，既对不齐、又会出现「有 SQL 内容、没执行时间」。
 *
 * <p><b>为什么挂在 {@code Executor} 上</b>：这样能看到所有语句，包括 XML Mapper 里的原生 SQL 和批量；
 * 也<b>不能</b>塞进 {@code MybatisPlusConfig} 里的 {@code MybatisPlusInterceptor} —— 那是
 * MyBatis-Plus 自己的内部拦截器链，只覆盖它构造的查询，看不到手写 SQL。
 *
 * <p>本仓库的 MyBatis-Plus 3.5.8 已移除内置的 {@code PerformanceInterceptor}，故自行实现。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        // 带 CacheKey/BoundSql 的重载：MyBatis-Plus 分页插件走这条
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlTimingInterceptor implements Interceptor {

    /** statement id → 它的 logger（与 MyBatis 打 SQL 内容用的是同一个） */
    private static final Map<String, Logger> STATEMENT_LOGGERS = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        String statementId = ((MappedStatement) invocation.getArgs()[0]).getId();
        Logger logger = loggerFor(statementId);
        boolean timed = logger.isDebugEnabled();
        long start = timed ? System.nanoTime() : 0L;
        try {
            return invocation.proceed();
        } finally {
            if (timed) {
                logger.debug("==> 执行时间：{}ms", (System.nanoTime() - start) / 1_000_000);
            }
        }
    }

    /** statement id 与 MyBatis 用同一个 logger 名，日志左侧那一列才会对齐 */
    private Logger loggerFor(String statementId) {
        return STATEMENT_LOGGERS.computeIfAbsent(statementId, LoggerFactory::getLogger);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无配置项
    }
}
