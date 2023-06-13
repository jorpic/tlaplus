package tlc2.repl;

import java.util.Collections;
import java.util.List;

public interface REPLCommand {
    interface Visitor {
        void visit(REPLCommand.Extends cmd);
    }

    void accept(Visitor visitor);

    static final class Extends implements REPLCommand {
        public final List<String> modules;

        public Extends(List<String> modules) {
            this.modules = Collections.unmodifiableList(modules);
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visit(this);
        }
    }
}