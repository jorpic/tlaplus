package tlc2;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tla2sany.parser.TLAplusParser;
import tla2sany.parser.SyntaxTreeNode;
import tla2sany.st.SyntaxTreeConstants;
import tla2sany.st.ParseError;


// FIXME: N_PostfixLHS (x^#), N_PrefixLHS (-.x)
class REPLParser {

    private List<String> moduleExtends = null;
    private SyntaxTreeNode[] heirs = null;

    public int parse(String expr) {
        TLAplusParser p = new TLAplusParser(
            // FIXME: use util.TLAConstants
            new StringReader("---- MODULE x ----\n" + expr + "\n===="));

        if (!p.parse()) {
            return -1;
            // ParseError[] errs = p.PErrors.errors();
            // for(int i = 0; i < errs.length; i++) {
            //     System.out.println(errs[i].reportedError());
            // }
        }

        SyntaxTreeNode root = (SyntaxTreeNode) p.rootNode();
        // root.printST(1); // FIXME: debug

        SyntaxTreeNode[] module = root.getHeirs();
        this.moduleExtends = Arrays.stream(module)
            .filter(n -> n != null && n.isKind(SyntaxTreeConstants.N_Extends))
            .flatMap(n -> Arrays.stream(n.getHeirs()))
            .filter(n -> n != null && n.isKind(231))
            .map(n -> n.getImage())
            .collect(Collectors.toList());

        if (this.moduleExtends.size() > 0) {
            return SyntaxTreeConstants.N_Extends;
        }

        SyntaxTreeNode body = Arrays.stream(module)
            .filter(n -> n != null && n.isKind(SyntaxTreeConstants.N_Body))
            .flatMap(n -> Arrays.stream(n.getHeirs()))
            .findFirst().orElse(null);

        if (body == null) {
            return -1;  // Empty body. E.g. when expr is an empty string.
        }

        this.heirs = body.getHeirs();
        return body.getKind();
    }

    public List<String> getExtendsModules() {
        return this.moduleExtends;
    }

    public String getOperatorName() {
        String identName = Arrays.stream(this.heirs)
            .filter(n -> n != null && n.isKind(SyntaxTreeConstants.N_IdentLHS))
            .flatMap(n -> Arrays.stream(n.getHeirs()))
            .filter(n -> n != null && n.isKind(231))
            .map(n -> n.getImage())
            .findFirst().orElse(null);

        if (identName != null) return identName;

        String infixName = Arrays.stream(this.heirs)
            .filter(n -> n != null && n.isKind(SyntaxTreeConstants.N_InfixLHS))
            .map(n -> n.getHeirs())
            .map(hs -> hs.length > 1 && hs[1] != null ? hs[1].getImage() : null)
            .findFirst().orElse(null);

        return infixName;
    }

    public String getFunctionName() {
        return Arrays.stream(this.heirs)
            .filter(n -> n != null && n.isKind(231))
            .map(n -> n.getImage())
            .findFirst().orElse(null);
    }
}
