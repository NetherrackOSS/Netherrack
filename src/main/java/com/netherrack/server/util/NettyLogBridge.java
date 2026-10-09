package com.netherrack.server.util;

import io.netty.util.internal.logging.AbstractInternalLogger;
import io.netty.util.internal.logging.FormattingTuple;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.MessageFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Routes Netty's logging - which CloudburstMC's Network and Protocol libraries log through
 * too - into Netherrack's own Logger. Without this, Netty finds slf4j-api on the classpath
 * with no backend behind it and every library message (errors included) is discarded.
 * <p>
 * Info, warnings and errors are always shown. Debug and trace are only shown when
 * "debug=true" is set in server.properties, and only from the CloudburstMC libraries -
 * Netty's own debug output is a dump of its internal settings, not useful for debugging
 * Netherrack. The one exception: the packet codec only reports packets that fail to encode
 * or decode at debug level, and those failures are otherwise completely silent (the packet
 * just never arrives), so they're always shown.
 */
public final class NettyLogBridge extends InternalLoggerFactory {

    private static final String CLOUDBURST_LOGGERS = "org.cloudburstmc.";
    private static final String PACKET_CODEC_LOGGER = "org.cloudburstmc.protocol.bedrock.netty.codec.packet";

    private static volatile boolean debug;

    private NettyLogBridge() {
    }

    /**
     * Must run before anything touches Netty, since Netty classes pick their logger once,
     * when they're first loaded.
     */
    public static void install() {
        InternalLoggerFactory.setDefaultFactory(new NettyLogBridge());
    }

    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    public static boolean isDebug() {
        return debug;
    }

    @Override
    protected InternalLogger newInstance(String name) {
        return new BridgedLogger(name);
    }

    private static final class BridgedLogger extends AbstractInternalLogger {

        private final boolean cloudburst;
        private final boolean alwaysDebug;

        private BridgedLogger(String name) {
            super(name);
            this.cloudburst = name.startsWith(CLOUDBURST_LOGGERS);
            this.alwaysDebug = name.startsWith(PACKET_CODEC_LOGGER);
        }

        @Override
        public boolean isTraceEnabled() {
            return debug && cloudburst;
        }

        @Override
        public boolean isDebugEnabled() {
            return (debug && cloudburst) || alwaysDebug;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public void trace(String msg) {
            if (isTraceEnabled()) Logger.debug(prefix(msg, null));
        }

        @Override
        public void trace(String format, Object arg) {
            if (isTraceEnabled()) Logger.debug(prefix(MessageFormatter.format(format, arg)));
        }

        @Override
        public void trace(String format, Object argA, Object argB) {
            if (isTraceEnabled()) Logger.debug(prefix(MessageFormatter.format(format, argA, argB)));
        }

        @Override
        public void trace(String format, Object... arguments) {
            if (isTraceEnabled()) Logger.debug(prefix(MessageFormatter.arrayFormat(format, arguments)));
        }

        @Override
        public void trace(String msg, Throwable t) {
            if (isTraceEnabled()) Logger.debug(prefix(msg, t));
        }

        @Override
        public void debug(String msg) {
            if (isDebugEnabled()) debugLine(prefix(msg, null));
        }

        @Override
        public void debug(String format, Object arg) {
            if (isDebugEnabled()) debugLine(prefix(MessageFormatter.format(format, arg)));
        }

        @Override
        public void debug(String format, Object argA, Object argB) {
            if (isDebugEnabled()) debugLine(prefix(MessageFormatter.format(format, argA, argB)));
        }

        @Override
        public void debug(String format, Object... arguments) {
            if (isDebugEnabled()) debugLine(prefix(MessageFormatter.arrayFormat(format, arguments)));
        }

        @Override
        public void debug(String msg, Throwable t) {
            if (isDebugEnabled()) debugLine(prefix(msg, t));
        }

        @Override
        public void info(String msg) {
            Logger.info(prefix(msg, null));
        }

        @Override
        public void info(String format, Object arg) {
            Logger.info(prefix(MessageFormatter.format(format, arg)));
        }

        @Override
        public void info(String format, Object argA, Object argB) {
            Logger.info(prefix(MessageFormatter.format(format, argA, argB)));
        }

        @Override
        public void info(String format, Object... arguments) {
            Logger.info(prefix(MessageFormatter.arrayFormat(format, arguments)));
        }

        @Override
        public void info(String msg, Throwable t) {
            Logger.info(prefix(msg, t));
        }

        @Override
        public void warn(String msg) {
            Logger.warn(prefix(msg, null));
        }

        @Override
        public void warn(String format, Object arg) {
            Logger.warn(prefix(MessageFormatter.format(format, arg)));
        }

        @Override
        public void warn(String format, Object... arguments) {
            Logger.warn(prefix(MessageFormatter.arrayFormat(format, arguments)));
        }

        @Override
        public void warn(String format, Object argA, Object argB) {
            Logger.warn(prefix(MessageFormatter.format(format, argA, argB)));
        }

        @Override
        public void warn(String msg, Throwable t) {
            Logger.warn(prefix(msg, t));
        }

        @Override
        public void error(String msg) {
            Logger.error(prefix(msg, null));
        }

        @Override
        public void error(String format, Object arg) {
            Logger.error(prefix(MessageFormatter.format(format, arg)));
        }

        @Override
        public void error(String format, Object argA, Object argB) {
            Logger.error(prefix(MessageFormatter.format(format, argA, argB)));
        }

        @Override
        public void error(String format, Object... arguments) {
            Logger.error(prefix(MessageFormatter.arrayFormat(format, arguments)));
        }

        @Override
        public void error(String msg, Throwable t) {
            Logger.error(prefix(msg, t));
        }

        private void debugLine(String line) {
            if (alwaysDebug) {
                Logger.warn(line);
            } else {
                Logger.debug(line);
            }
        }

        private String prefix(FormattingTuple tuple) {
            return prefix(tuple.getMessage(), tuple.getThrowable());
        }

        /**
         * Tags the line with the short class name it came from, so library messages are
         * distinguishable from Netherrack's own, and appends the stack trace if there is one.
         */
        private String prefix(String message, Throwable t) {
            String source = name().substring(name().lastIndexOf('.') + 1);
            String line = "(" + source + ") " + message;
            if (t == null) {
                return line;
            }
            StringWriter trace = new StringWriter();
            t.printStackTrace(new PrintWriter(trace));
            return line + System.lineSeparator() + trace.toString().stripTrailing();
        }
    }
}
