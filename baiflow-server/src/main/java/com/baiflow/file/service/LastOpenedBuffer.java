package com.baiflow.file.service;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「上次打开时间」的待落库缓冲：进入目录时写入，由 {@code LastOpenedFlushScheduler} 定时批量刷库。
 * <p>
 * <b>为什么异步</b>：进入目录本来是一次 GET，同步 UPDATE 会在读路径上多一次往返（远端库约 40–75ms）
 * 并拿行锁；改为攒起来批量写，每个刷库周期一次往返。
 * <p>
 * 缓冲里只留目录 id，落库时统一取当时的时间 —— 这些目录的「上次打开」都发生在最近一个周期内，
 * 精度够用（长摁弹窗只显示到秒级），换来的是**一条** UPDATE 写完整批。
 */
@Component
public class LastOpenedBuffer {

    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    /** 记一次进入目录（同一目录重复进只留一份） */
    public void touch(String fileItemId) {
        if (fileItemId != null && !fileItemId.isBlank()) {
            pending.add(fileItemId);
        }
    }

    /**
     * 取出并清空待落库的目录 id。
     * <p>用迭代器逐个移除，而不是「先拷贝再清空」：后者会丢掉拷贝与清空之间新进来的 touch。
     */
    public Set<String> drain() {
        Set<String> taken = new HashSet<>();
        for (Iterator<String> it = pending.iterator(); it.hasNext(); ) {
            taken.add(it.next());
            it.remove();
        }
        return taken;
    }
}
