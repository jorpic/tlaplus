package tlc2.repl;

@SuppressWarnings("serial")
public class REPLException extends Exception {
    public REPLException(String errorMessage) {
        super(errorMessage);
    }

    public REPLException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
