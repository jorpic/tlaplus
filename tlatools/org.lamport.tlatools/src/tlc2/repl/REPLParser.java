package tlc2.repl;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import tla2sany.parser.TLAplusParser;
import tla2sany.parser.SyntaxTreeNode;
import tla2sany.st.SyntaxTreeConstants;

/**
 * FIXME
 */
public class REPLParser {

    @SuppressWarnings("serial")
    public abstract static class Exception extends java.lang.Exception {
        public Exception(String errorMessage) {
            super(errorMessage);
        }

        public static final class ParseFailed extends Exception {
            ParseFailed() { super("Parse failed"); }
        }

        public static final class EmptyBody extends Exception {
            EmptyBody() { super("Empty body"); }
        }

        public static final class UnsupportedCommand extends Exception {
            public final String nodeKind;

            UnsupportedCommand(String nodeKind) {
                super("Unsupported REPL command");
                this.nodeKind = nodeKind;
            }
        }
    }

    // This is somehow missing in SyntaxTreeConstants.
    private static final int N_Ident = 231;

    /**
     * FIXME
     * note that it parses only single TLA+ statement
     * @return
     */
    public static REPLCommand parse(String str) throws REPLParser.Exception {
        TLAplusParser p = new TLAplusParser(
            new StringReader("---- MODULE REPLParser ----\n" + str + "\n===="));

        if (!p.parse()) {
            // FIXME: add p.PErrors.errors() to exception.
            throw new REPLParser.Exception.ParseFailed();
        }

        SyntaxTreeNode root = (SyntaxTreeNode) p.rootNode();
        SyntaxTreeNode[] module = root.getHeirs();
        List<String> moduleExtends = Arrays.stream(module)
            .filter(n -> n != null && n.isKind(SyntaxTreeConstants.N_Extends))
            .flatMap(n -> Arrays.stream(n.getHeirs()))
            .filter(n -> n != null && n.isKind(N_Ident))
            .map(n -> n.getImage())
            .collect(Collectors.toList());

        if (moduleExtends.size() > 0) {
            return new REPLCommand.Extends(moduleExtends);
        }

        SyntaxTreeNode body = Arrays.stream(module)
            .filter(n -> n != null && n.isKind(SyntaxTreeConstants.N_Body))
            .flatMap(n -> Arrays.stream(n.getHeirs()))
            .findFirst().orElse(null);

        if (body == null) {
            throw new REPLParser.Exception.EmptyBody();
        }

        String nodeKind = SyntaxTreeConstants.SyntaxNodeImage[body.getKind()].toString();
        throw new REPLParser.Exception.UnsupportedCommand(nodeKind);
    }
}