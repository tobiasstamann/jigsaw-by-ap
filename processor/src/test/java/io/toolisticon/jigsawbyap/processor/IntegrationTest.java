package io.toolisticon.jigsawbyap.processor;

import io.toolisticon.annotationprocessortoolkit.tools.MessagerUtils;
import io.toolisticon.compiletesting.CompileTestBuilder;
import io.toolisticon.compiletesting.JavaFileObjectUtils;
import org.junit.Before;
import org.junit.Test;

public class IntegrationTest {

    @Before
    public void init() {
        MessagerUtils.setPrintMessageCodes(true);
    }

    @Test
    //@Ignore
    public void testValidUsage() {


        CompileTestBuilder.compilationTest()
                .addSources(
                        JavaFileObjectUtils.readFromResource("testcases.moduleInfoAlreadyExits/module-info.java"),
                        JavaFileObjectUtils.readFromResource("testcases.moduleInfoAlreadyExits/Test.java"),
                        JavaFileObjectUtils.readFromResource("testcases.moduleInfoAlreadyExits/package-info.java")
                )
                .addProcessors(JigsawByApProcessor.class)
                .useModules("jigsawbyap.api")
                .compilationShouldFail()
                .expectedErrorMessages(JigsawByApProcessorMessages.ERROR_MODULE_INFO_ALREADY_EXISTS.getCode())
                .testCompilation();

    }

}
