package tlc2;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;


class REPLState {
    private Set<String> moduleExtends = new HashSet<String>();
    private Map<String, Integer> definitions = new HashMap<String, Integer>();
    private List<String> lines = new ArrayList<String>();

    private List<String> newExtends = new ArrayList<String>();
    private String newDefinition = null;
    private String newLine = null;


    public void addModule(final String name) {
        newExtends.add(name);
    }

    // FIXME: NB. only one definition is supported!
    public void addDefinition(final String name, final String def) {
        newDefinition = name;
        newLine = def;
    }

    public void addCommand(final String cmd) {
        newLine = cmd;
    }

    public void revert() {
        newExtends.clear();
        newDefinition = null;
        newLine = null;
    }

    public void commit() {
        moduleExtends.addAll(newExtends);

        if (newDefinition != null) {
            final Integer defIx = definitions.get(newDefinition);
            if(defIx != null) {
                lines.add(defIx, newLine);
            } else {
                definitions.put(newDefinition, lines.size());
                lines.add(newLine);
            }
        }

        newExtends.clear();
        newDefinition = null;
        newLine = null;
    }

    public Stream<String> getModules() {
        return Stream.concat(moduleExtends.stream(), newExtends.stream());
    }

    public Stream<String> getLines() {
        if (newDefinition == null) {
            if (newLine == null) {
                return lines.stream();
            } else {
                return Stream.concat(lines.stream(), Stream.of(newLine));
            }
        } else {
            final Integer defIx = definitions.get(newDefinition);
            if (defIx == null) {
                // new definition
                return Stream.concat(lines.stream(), Stream.of(newLine));
            } else {
                // definition override
                return IntStream.range(0, lines.size())
                    .mapToObj(i -> i == defIx ? newLine : lines.get(i));
            }
        }
    }
}
