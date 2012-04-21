package xjavax.management;

public class JMException extends java.lang.Exception   { 
    
    /* Serial version */
    private static final long serialVersionUID = 350520924977331825L;

    /**
     * Default constructor.
     */
    public JMException() {
    super();
    }
    
    /**
     * Constructor that allows a specific error message to be specified.
     *
     * @param msg the detail message.
     */
    public JMException(String msg) {
    super(msg);
    }
    
}
