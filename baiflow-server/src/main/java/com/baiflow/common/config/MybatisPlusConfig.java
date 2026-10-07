package com.baiflow.common.config;

import com.baiflow.common.db.SqlTimingInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.baiflow.**.mapper")
public class MybatisPlusConfig {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * SQL 计时拦截器：注册为独立 @Bean（mybatis-spring-boot-starter 会把容器里的
     * {@code Interceptor} 自动挂进 SqlSessionFactory）。
     * <b>不能</b>塞进上面的 {@code MybatisPlusInterceptor} —— 那是 MyBatis-Plus 自己的内部链，
     * 只覆盖它构造的查询，看不到 XML Mapper 里的原生 SQL。
     */
    @Bean
    SqlTimingInterceptor sqlTimingInterceptor() {
        return new SqlTimingInterceptor();
    }
}
