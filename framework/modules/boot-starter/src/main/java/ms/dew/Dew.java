/*
 * Copyright 2019. the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ms.dew;

import com.ecfront.dew.common.$;
import com.ecfront.dew.common.StandardCode;
import com.fasterxml.jackson.databind.SerializationFeature;
import ms.dew.core.DewConfig;
import ms.dew.core.DewContext;
import ms.dew.core.auth.AuthAdapter;
import ms.dew.core.auth.BasicAuthAdapter;
import ms.dew.core.basic.fun.VoidExecutor;
import ms.dew.core.basic.fun.VoidPredicate;
import ms.dew.core.basic.loading.DewLoadImmediately;
import ms.dew.core.basic.utils.NetUtils;
import ms.dew.core.cluster.*;
import ms.dew.notification.Notify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.JacksonProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dew核心操作类.
 *
 * @author gudaoxuri
 */
@EnableConfigurationProperties(DewConfig.class)
@Configuration
public class Dew {

    private static final Logger logger = LoggerFactory.getLogger(Dew.class);

    /**
     * The constant cluster.
     */
    public static Cluster cluster = new Cluster();
    /**
     * The constant notify.
     */
    public static Notify notify = null;
    /**
     * The constant applicationContext.
     */
    public static ApplicationContext applicationContext;
    /**
     * The constant dewConfig.
     */
    public static DewConfig dewConfig;

    /**
     * The constant auth.
     */
    public static AuthAdapter auth;

    @Value("${spring.application.name:please-setting-this}")
    private String applicationName;
    @Value(("${spring.profiles.active:default}"))
    private String profile;
    @Value("${server.port:-1}")
    private int serverPort;

    @Autowired
    private DewConfig injectDewConfig;
    @Autowired
    private ApplicationContext injectApplicationContext;
    @Autowired(required = false)
    private JacksonProperties jacksonProperties;

    /**
     * 获取请求上下文信息.
     *
     * @return 请求上下文信息 dew context
     */
    public static DewContext context() {
        return DewContext.getContext();
    }

    /**
     * Init.
     *
     * @throws IOException            the io exception
     * @throws ClassNotFoundException the class not found exception
     */
    @PostConstruct
    public void init() throws IOException, ClassNotFoundException {
        logger.info("Load Auto Configuration : {}", this.getClass().getName());

        logger.info("Load Dew basic info...");
        Dew.dewConfig = injectDewConfig;
        Dew.applicationContext = injectApplicationContext;
        Info.name = applicationName;
        Info.profile = profile;
        Info.webPort = serverPort;
        Info.instance = applicationName + "@" + Info.profile + "@" + Info.ip + ":" + serverPort;
        Cluster.init(Info.name, Info.instance);

        Notify.init(Dew.dewConfig.getNotifies(), flag ->
                " FROM " + Dew.Info.instance + " BY " + flag);
        Dew.notify = new Notify();

        // Support java8 Time
        if (jacksonProperties != null) {
            jacksonProperties.getSerialization().put(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        }
        // Load Auth Adapter
        auth = Dew.applicationContext.getBean(BasicAuthAdapter.class);
        logger.info("Use Auth Adapter:" + auth.getClass().getName());

        logger.info("Load Dew cluster...");
        if (Dew.applicationContext.containsBean(injectDewConfig.getCluster().getCache() + "ClusterCache")) {
            Dew.cluster.cache = (ClusterCache) Dew.applicationContext.getBean(injectDewConfig.getCluster().getCache() + "ClusterCache");
        }
        if (Dew.applicationContext.containsBean(injectDewConfig.getCluster().getLock() + "ClusterLock")) {
            Dew.cluster.lock = (ClusterLockWrap) Dew.applicationContext.getBean(injectDewConfig.getCluster().getLock() + "ClusterLock");
        }
        if (Dew.applicationContext.containsBean(injectDewConfig.getCluster().getMap() + "ClusterMap")) {
            Dew.cluster.map = (ClusterMapWrap) Dew.applicationContext.getBean(injectDewConfig.getCluster().getMap() + "ClusterMap");
        }
        if (Dew.applicationContext.containsBean(injectDewConfig.getCluster().getMq() + "ClusterMQ")) {
            Dew.cluster.mq = (ClusterMQ) Dew.applicationContext.getBean(injectDewConfig.getCluster().getMq() + "ClusterMQ");
        }
        if (Dew.applicationContext.containsBean(injectDewConfig.getCluster().getElection() + "ClusterElection")) {
            Dew.cluster.election =
                    (ClusterElectionWrap) Dew.applicationContext.getBean(injectDewConfig.getCluster().getElection() + "ClusterElection");
        }
        if (dewConfig.getCluster().getConfig().isHaEnabled()) {
            Cluster.ha(dewConfig.getCluster().getConfig().getHa());
        }

        logger.info("Load Dew funs...");
        // Load Immediately
        Set<Class<?>> loadOrders = $.clazz.scan(Dew.class.getPackage().getName(), new HashSet<Class<? extends Annotation>>() {
            {
                add(DewLoadImmediately.class);
            }
        }, null);
        loadOrders.forEach(loadOrder -> Dew.applicationContext.getBean(loadOrder));
    }

    /**
     * 组件基础信息.
     */
    public static class Info {
        /**
         * 应用名称.
         */
        public static String name;
        /**
         * 应用环境.
         */
        public static String profile;
        /**
         * 应用主机IP.
         */
        public static String ip;
        /**
         * 应用主机名.
         */
        public static String host;
        /**
         * 应用主机Web端口.
         */
        public static int webPort;
        /**
         * 应用实例，各组件唯一.
         */
        public static String instance;

        static {
            InetAddress inetAddress = NetUtils.getLocalAddress();
            ip = inetAddress.getHostAddress();
            host = inetAddress.getHostName();
        }

    }

    /**
     * 定时器支持（带请求上下文绑定）.
     */
    public static class Timer {

        private static final Logger logger = LoggerFactory.getLogger(Timer.class);

        /**
         * Periodic.
         *
         * @param initialDelaySec the initial delay sec
         * @param periodSec       the period sec
         * @param fun             the fun
         */
        public static void periodic(long initialDelaySec, long periodSec, VoidExecutor fun) {
            DewContext context = Dew.context();
            $.timer.periodic(initialDelaySec, periodSec, true, () -> {
                DewContext.setContext(context);
                try {
                    fun.exec();
                } catch (Exception e) {
                    logger.error("[Timer] Execute error", e);
                }
            });
        }

        /**
         * Periodic.
         *
         * @param periodSec the period sec
         * @param fun       the fun
         */
        public static void periodic(long periodSec, VoidExecutor fun) {
            periodic(0, periodSec, fun);
        }

        /**
         * Timer.
         *
         * @param delaySec the delay sec
         * @param fun      the fun
         */
        public static void timer(long delaySec, VoidExecutor fun) {
            DewContext context = Dew.context();
            $.timer.timer(delaySec, () -> {
                DewContext.setContext(context);
                try {
                    fun.exec();
                } catch (Exception e) {
                    logger.error("[Timer] Execute error", e);
                }
            });
        }
    }

    /**
     * 常用工具.
     */
    public static class Util {

        private static ExecutorService executorService = Executors.newCachedThreadPool();

        /**
         * Gets real ip.
         *
         * @param request the request
         * @return the real ip
         */
        public static String getRealIP(HttpServletRequest request) {
            Map<String, String> requestHeader = new HashMap<>();
            Enumeration<String> header = request.getHeaderNames();
            while (header.hasMoreElements()) {
                String key = header.nextElement();
                requestHeader.put(key, request.getHeader(key));
            }
            return getRealIP(requestHeader, request.getRemoteAddr());
        }

        /**
         * Gets real ip.
         *
         * @param requestHeader the request header
         * @param remoteAddr    the remote addr
         * @return the real ip
         */
        public static String getRealIP(Map<String, String> requestHeader, String remoteAddr) {
            Map<String, String> formattedRequestHeader = new HashMap<>();
            requestHeader.forEach((k, v) -> formattedRequestHeader.put(k.toLowerCase(), v));
            if (formattedRequestHeader.containsKey("x-forwarded-for")
                    && formattedRequestHeader.get("x-forwarded-for") != null
                    && !formattedRequestHeader.get("x-forwarded-for").isEmpty()) {
                return formattedRequestHeader.get("x-forwarded-for");
            }
            if (formattedRequestHeader.containsKey("wl-proxy-client-ip")
                    && formattedRequestHeader.get("wl-proxy-client-ip") != null
                    && !formattedRequestHeader.get("wl-proxy-client-ip").isEmpty()) {
                return formattedRequestHeader.get("wl-proxy-client-ip");
            }
            if (formattedRequestHeader.containsKey("x-forwarded-host")
                    && formattedRequestHeader.get("x-forwarded-host") != null
                    && !formattedRequestHeader.get("x-forwarded-host").isEmpty()) {
                return formattedRequestHeader.get("x-forwarded-host");
            }
            return remoteAddr;
        }

        /**
         * New thread.
         *
         * @param fun the fun
         */
        public static void newThread(Runnable fun) {
            executorService.execute(fun);
        }

        /**
         * Runnable with context.
         */
        public static class RunnableWithContext implements Runnable {

            private VoidExecutor fun;
            private DewContext context;

            /**
             * Instantiates a new Runnable with context.
             *
             * @param fun the fun
             */
            public RunnableWithContext(VoidExecutor fun) {
                this.fun = fun;
                this.context = DewContext.getContext();
            }

            @Override
            public void run() {
                DewContext.setContext(context);
                fun.exec();
            }
        }

    }

    /**
     * 异常输出.
     */
    public static class E {

        /**
         * 异常处理-重用Http状态.
         *
         * @param <E>  the type parameter
         * @param code 异常编码
         * @param ex   异常类型
         * @return the e
         */
        public static <E extends Throwable> E e(String code, E ex) {
            return e(code, ex, -1);
        }

        /**
         * 异常处理-重用Http状态.
         *
         * @param <E>            the type parameter
         * @param code           异常编码
         * @param ex             异常类型
         * @param customHttpCode 自定义Http状态码
         * @return the e
         */
        public static <E extends Throwable> E e(String code, E ex, StandardCode customHttpCode) {
            return e(code, ex, Integer.valueOf(customHttpCode.toString()));
        }

        /**
         * 异常处理-重用Http状态.
         *
         * @param <E>            the type parameter
         * @param code           异常编码
         * @param ex             异常类型
         * @param customHttpCode 自定义Http状态码
         * @return the e
         */
        public static <E extends Throwable> E e(String code, E ex, int customHttpCode) {
            try {
                $.bean.setValue(ex, "detailMessage", $.json.createObjectNode()
                        .put("code", code)
                        .put("message", ex.getLocalizedMessage())
                        .put("customHttpCode", customHttpCode)
                        .toString());
            } catch (NoSuchFieldException e1) {
                logger.error("Throw Exception Convert error", e1);
            }
            return ex;
        }

        /**
         * Check not null.
         *
         * @param <E> the type parameter
         * @param obj the obj
         * @param ex  the ex
         */
        public static <E extends RuntimeException> void checkNotNull(Object obj, E ex) {
            check(() -> obj == null, ex);
        }

        /**
         * Check not empty.
         *
         * @param <E>     the type parameter
         * @param objects the objects
         * @param ex      the ex
         */
        public static <E extends RuntimeException> void checkNotEmpty(Iterable<?> objects, E ex) {
            check(() -> objects == null || !objects.iterator().hasNext(), ex);
        }

        /**
         * Check not empty.
         *
         * @param <E>     the type parameter
         * @param objects the objects
         * @param ex      the ex
         */
        public static <E extends RuntimeException> void checkNotEmpty(Map<?, ?> objects, E ex) {
            check(() -> objects == null || objects.size() == 0, ex);
        }

        /**
         * 抛出不符合预期异常.
         *
         * @param <E>         the type parameter
         * @param notExpected 不符合预期的情况
         * @param ex          异常
         */
        public static <E extends RuntimeException> void check(boolean notExpected, E ex) {
            check(() -> notExpected, ex);
        }

        /**
         * 抛出不符合预期异常.
         *
         * @param <E>         the type parameter
         * @param notExpected 不符合预期的情况
         * @param ex          异常
         */
        public static <E extends RuntimeException> void check(VoidPredicate notExpected, E ex) {
            if (notExpected.test()) {
                throw ex;
            }
        }

        /**
         * 抛出不符合预期异常.
         *
         * @param notExpected 不符合预期的情况
         */
        public static void check(boolean notExpected) {
            check(() -> notExpected);
        }

        /**
         * 抛出不符合预期异常.
         *
         * @param notExpected 不符合预期的情况
         */
        public static void check(VoidPredicate notExpected) {
            if (notExpected.test()) {
                throw new AssertionError("Checked error.");
            }
        }

    }

}
