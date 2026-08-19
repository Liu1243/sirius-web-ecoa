package org.eclipse.sirius.web.edt.importexport;

public class FailedImportException extends Exception {
    private static final long serialVersionUID = 1L;

    public FailedImportException(String message) {
        super(message);
    }

    public FailedImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
