package pyjava.tree;

import java.util.LinkedList;
import java.util.Objects;

public class IndentationAwareAppender implements LazyAppendable {
    private LinkedList<LazyAppendable> appendables = new LinkedList<>();

    @Override
    public <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T {
        for (var appendable : appendables) {
            appendable.doAppend(a, state);
        }
    }

    public IndentationAwareAppender append(char c) {
        appendables.addLast(new AppendChar(c));
        return this;
    }

    protected static record AppendChar(char c) implements LazyAppendable {
        @Override
        public <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T {
            a.append(c);
        }
    }

    public IndentationAwareAppender append(CharSequence str) {
        appendables.addLast(new AppendCharSequence(str));
        return this;
    }

    protected static record AppendCharSequence(CharSequence str) implements LazyAppendable {
        public AppendCharSequence {
            Objects.requireNonNull(str);
        }

        @Override
        public <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T {
            a.append(str);
        }
    }

    public IndentationAwareAppender append(CharSequence str, int start, int end) {
        appendables.addLast(new AppendCharSequenceFromTo(str, start, end));
        return this;
    }

    protected static record AppendCharSequenceFromTo(CharSequence str, int start, int end) implements LazyAppendable {
        public AppendCharSequenceFromTo {
            Objects.checkFromToIndex(start, end, str.length());
        }

        @Override
        public <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T {
            a.append(str, start, end);   
        }
    }

    public IndentationAwareAppender decrIndentNewline() {
        if (!appendables.isEmpty() && appendables.getLast() instanceof Newline) {
            appendables.add(appendables.size() - 1, DecrIndent.DEFAULT_INSTANCE);
            return this;
        } else {
            return decrIndent().newline();
        }
    }

    public IndentationAwareAppender newline() {
        appendables.addLast(Newline.DEFAULT_INSTANCE);
        return this;
    }

    protected static class Newline implements LazyAppendable {
        public static final Newline DEFAULT_INSTANCE = new Newline();

        @Override
        public <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T {
            a.append('\n');
            synchronized (state) {
                for (int i = 0; i < state.indent; i++) {
                    a.append("    ");
                }
            }
        }
    }

    public IndentationAwareAppender incrIndent() {
        if (!appendables.isEmpty()) {
            var last = appendables.getLast();
            if (last instanceof IncrIndent oldIncrIndent) {
                appendables.set(appendables.size() - 1, new IncrIndent(oldIncrIndent.amount() + 1));
                return this;
            }
            if (last instanceof DecrIndent oldDecrIndent) {
                if (oldDecrIndent.amount() == 1) {
                    appendables.removeLast();
                } else {
                    appendables.set(appendables.size() - 1, new DecrIndent(oldDecrIndent.amount() - 1));
                }
                return this;
            }
        }
        appendables.addLast(IncrIndent.DEFAULT_INSTANCE);
        return this;
    }

    protected static record IncrIndent(int amount) implements LazyAppendable {
        public static final IncrIndent DEFAULT_INSTANCE = new IncrIndent(1);

        public IncrIndent {
            if (amount < 1) {
                throw new IllegalArgumentException("invalid increase indent amount");
            }
        }

        @Override
        public <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T {
            state.indent += amount;
        }
    }

    public IndentationAwareAppender decrIndent() {
        if (!appendables.isEmpty()) {
            var last = appendables.getLast();
            if (last instanceof DecrIndent oldDecrIndent) {
                appendables.set(appendables.size() - 1, new DecrIndent(oldDecrIndent.amount() + 1));
                return this;
            }
            if (last instanceof IncrIndent oldIncrIndent) {
                if (oldIncrIndent.amount() == 1) {
                    appendables.removeLast();
                } else {
                    appendables.set(appendables.size() - 1, new IncrIndent(oldIncrIndent.amount() - 1));
                }
                return this;
            }
        }
        appendables.addLast(DecrIndent.DEFAULT_INSTANCE);
        return this;
    }

    protected static record DecrIndent(int amount) implements LazyAppendable {
        public static final DecrIndent DEFAULT_INSTANCE = new DecrIndent(1);

        public DecrIndent {
            if (amount < 1) {
                throw new IllegalArgumentException("invalid decrease indent amount");
            }
        }

        @Override
        public <T extends Throwable> void doAppend(AppendFunction<? extends T> a, AppenderState state) throws T {
            if ((state.indent -= amount) < 0) {
                throw new IllegalStateException("cannot decrease indent, already at 0");
            }
        }
    }

    public IndentationAwareAppender later() {
        var result = new IndentationAwareAppender();
        appendables.addLast(result);
        return result;
    }
}
