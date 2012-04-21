package xjavax.management;

public class MBeanException extends JMException   { 
    
    
    /* Serial version */
    private static final long serialVersionUID = 4066342430588744142L;

    /**
     * @serial Encapsulated {@link Exception}
     */
    private java.lang.Exception exception ;
   

    /**
     * Creates an <CODE>MBeanException</CODE> that wraps the actual <CODE>java.lang.Exception</CODE>.
     *
     * @param e the wrapped exception.
     */
    public MBeanException(java.lang.Exception e) { 
    super() ;
    exception = e ;
    } 

    /**
     * Creates an <CODE>MBeanException</CODE> that wraps the actual <CODE>java.lang.Exception</CODE> with
     * a detail message.
     *
     * @param e the wrapped exception.
     * @param message the detail message.
     */
    public MBeanException(java.lang.Exception e, String message) { 
    super(message) ;
    exception = e ;
    } 
    

    /**
     * Return the actual {@link Exception} thrown.
     *
     * @return the wrapped exception.
     */
    public Exception getTargetException()  { 
    return exception;
    } 

    /**
     * Return the actual {@link Exception} thrown.
     *
     * @return the wrapped exception.
     */
    public Throwable getCause() {
    return exception;
    }
}