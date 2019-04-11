package io.toolisticon.jigsawbyap.processor;


import io.toolisticon.annotationprocessortoolkit.tools.corematcher.ValidationMessage;
import io.toolisticon.jigsawbyap.api.JigsawModule;

/**
 * Messages used by annotation processors.
 */
public enum JigsawByApProcessorMessages implements ValidationMessage {


    ERROR_COULD_NOT_FIND_MODULE_FILE("JIGSAW_BY_AP_ERROR_001", "Could not find module files"),
    ERROR_MODULE_ANNOTATIN_MUST_BE_PLACEDIN_ROOT_PACKAGE_INFO("JIGSAW_BY_AP_ERROR_002", JigsawModule.class.getCanonicalName() + " annotations must be placed in root package's package-info.java"),
    ERROR_MODULE_INFO_ALREADY_EXISTS("JIGSAW_BY_AP_ERROR_003", "module-info.java already exists"),
    ERROR_MODULE_NAME_IS_EMPTY("JIGSAW_BY_AP_ERROR_004", "module name must not be empty");


    /**
     * the message code.
     */
    private final String code;
    /**
     * the message text.
     */
    private final String message;

    /**
     * Constructor.
     *
     * @param code    the message code
     * @param message the message text
     */
    JigsawByApProcessorMessages(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * Gets the code of the message.
     *
     * @return the message code
     */
    public String getCode() {
        return this.code;
    }

    /**
     * Gets the message text.
     *
     * @return the message text
     */
    public String getMessage() {
        return message;
    }


}
