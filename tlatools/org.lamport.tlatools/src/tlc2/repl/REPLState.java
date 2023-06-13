package tlc2.repl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;


public class REPLState implements REPLCommand.Visitor {
    private Set<String> moduleExtends = new LinkedHashSet<String>();

    private List<String> newExtends= new ArrayList<String>();

    public void addModule(String name) {
        newExtends.add(name);
    }

    public void addModules(Collection<String> names) {
        newExtends.addAll(names);
    }

    public void revert() {
        newExtends.clear();
    }

    public void commit() {
        moduleExtends.addAll(newExtends);
        newExtends.clear();
    }

    public Stream<String> getModules() {
        return Stream.concat(moduleExtends.stream(), newExtends.stream()).distinct();
    }

    public void apply(REPLCommand cmd) {
        cmd.accept(this);
    }

    @Override
    public void visit(REPLCommand.Extends cmd) {
        this.addModules(cmd.modules);
    }
}