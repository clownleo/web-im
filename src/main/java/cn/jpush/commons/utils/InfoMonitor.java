package cn.jpush.commons.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 用于开发时定时输出{@linkplain InfoTask#getInfo()}以监听信息。如im项目的连接池大小
 * Created by leolin on 16/3/24.
 */
public class InfoMonitor {
    private static List<InfoTask> taskList = new LinkedList<>();
    private static Logger logger = LoggerFactory.getLogger(InfoMonitor.class);
    private static Timer InfoTimer = new Timer();

    static {
        InfoTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (InfoTask task : taskList) {
                    try {
                        logger.debug(task.getInfo());
                    } catch (Exception e) {
                        logger.debug("【信息监听异常】", e);
                    }
                }
            }
        }, 5 * 1000, 3000);
    }

    public static interface InfoTask {
        String getInfo();
    }

    /**
     * 十分低频的操作,一般只在初始化中调用，直接使用同步锁实现线程安全
     */
    public static synchronized void addTask(InfoTask task){
        if(logger.isDebugEnabled()) {
            taskList.add(task);
        }
    }
}
