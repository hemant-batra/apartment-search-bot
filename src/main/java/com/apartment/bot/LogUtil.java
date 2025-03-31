package com.apartment.bot;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LogUtil {

    private final List<String> logs = new ArrayList<>();

    public void println(String msg) {
        System.out.println(msg);
        logs.add(msg);
    }

    public void clearLogs() {
        logs.clear();
    }

    public List<String> getLogs() {
        return logs;
    }
}
