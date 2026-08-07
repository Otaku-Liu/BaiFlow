package com.baiflow.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 语言工具类 — 按当前请求语言翻译消息文本。
 * <p>
 * 以「中文文本即 key」的方式查词条：传入的字符串是中文默认文案，
 * 在对应语言的词条文件（{@code i18n/messages*.properties}）中查找翻译，
 * 找不到（如动态拼接的内容）则原样返回中文。
 * 语言由请求头 {@code Accept-Language} 决定，默认中文。
 * </p>
 */
@Component
public class I18nUtil {

    @Autowired
    private MessageSource messageSource;

    /**
     * 翻译消息。传入中文默认文案，返回当前请求语言对应的译文；
     * 输入为空或找不到对应词条时原样返回。
     */
    public String translate(String msg) {
        return translate(msg, LocaleContextHolder.getLocale());
    }

    /**
     * 翻译消息，使用显式指定的语言（用于安全过滤器链等无法从
     * {@link LocaleContextHolder} 取到请求语言的场景）。
     */
    public String translate(String msg, Locale locale) {
        if (msg == null || msg.isBlank()) {
            return msg;
        }
        Locale l = locale != null ? locale : LocaleContextHolder.getLocale();
        return messageSource.getMessage(msg, null, msg, l);
    }
}
