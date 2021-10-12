package pyjava.tree;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

public interface LazyAppendable {
    <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T;

    public static final class AppenderState {
        int indent;
    }

    @FunctionalInterface
    public static interface AppendFunction<T extends Throwable> {
        void append(CharSequence str, int start, int end) throws T;

        default void append(CharSequence str) throws T {
            if (str == null) str = "null";
            append(str, 0, str.length());
        }

        default void append(char c) throws T {
            append(Character.toString(c));
        }

        public static AppendFunctionNoThrow wrap(final PrintStream ps) {
            Objects.requireNonNull(ps);
            return new AppendFunctionNoThrow() {
                @Override
                public void append(CharSequence str, int start, int end) {
                    ps.append(str, start, end);
                }

                @Override
                public void append(CharSequence str) {
                    ps.append(str);
                }

                @Override
                public void append(char ch) {
                    ps.append(ch);
                }
            };
        }

        public static AppendFunctionNoThrow wrap(final PrintWriter pw) {
            Objects.requireNonNull(pw);
            return new AppendFunctionNoThrow() {
                @Override
                public void append(CharSequence str, int start, int end) {
                    pw.append(str, start, end);
                }

                @Override
                public void append(CharSequence str) {
                    pw.append(str);
                }

                @Override
                public void append(char c) {
                    pw.write(c);
                }
            };
        }

        public static AppendFunctionNoThrow wrap(final StringBuilder sb) {
            Objects.requireNonNull(sb);
            return new AppendFunctionNoThrow() {
                @Override
                public void append(CharSequence str, int start, int end) {
                    sb.append(str, start, end);
                }

                @Override
                public void append(CharSequence str) {
                    sb.append(str);
                }

                @Override
                public void append(char c) {
                    sb.append(c);
                }
            };
        }

        public static AppendFunctionNoThrow wrap(final StringBuffer sb) {
            Objects.requireNonNull(sb);
            return new AppendFunctionNoThrow() {
                @Override
                public void append(CharSequence str, int start, int end) {
                    sb.append(str, start, end);
                }

                @Override
                public void append(CharSequence str) {
                    sb.append(str);
                }

                @Override
                public void append(char c) {
                    sb.append(c);
                }
            };
        }

        public static AppendFunction<IOException> wrap(final Appendable a) {
            Objects.requireNonNull(a);
            return new AppendFunction<>() {
                @Override
                public void append(CharSequence str, int start, int end) throws IOException {
                    a.append(str, start, end);
                }

                @Override
                public void append(CharSequence str) throws IOException {
                    a.append(str);
                }

                @Override
                public void append(char c) throws IOException {
                    a.append(c);
                }
            };
        }

        public static AppendFunction<IOException> wrap(final OutputStream o) {
            return wrap(o, Charset.defaultCharset());
        }

        public static AppendFunction<IOException> wrap(final OutputStream o, final Charset cs) {
            Objects.requireNonNull(o);
            Objects.requireNonNull(cs);
            return new AppendFunction<>() {
                @Override
                public void append(CharSequence str, int start, int end) throws IOException {
                    var cbuf = CharBuffer.wrap(str, start, end);
                    var bbuf = cs.encode(cbuf);
                    assert bbuf.hasArray();
                    o.write(bbuf.array());
                }

                @Override
                public void append(CharSequence str) throws IOException {
                    var cbuf = CharBuffer.wrap(str);
                    var bbuf = cs.encode(cbuf);
                    assert bbuf.hasArray();
                    o.write(bbuf.array());
                }

                @Override
                public void append(char c) throws IOException {
                    var cbuf = CharBuffer.wrap(new char[] {c});
                    var bbuf = cs.encode(cbuf);
                    assert bbuf.hasArray();
                    o.write(bbuf.array());
                }
            };
        }
    }

    @FunctionalInterface
    public static interface AppendFunctionNoThrow extends AppendFunction<RuntimeException> {
        void append(CharSequence str, int start, int end);

        default void append(CharSequence str) {
            AppendFunction.super.append(str);
        }

        default void append(char c) {
            AppendFunction.super.append(c);
        }
    }
}