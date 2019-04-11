package io.toolisticon.jigsawbyap.processor;

import io.toolisticon.annotationprocessortoolkit.tools.MessagerUtils;
import io.toolisticon.compiletesting.CompileTestBuilder;
import io.toolisticon.compiletesting.JavaFileObjectUtils;
import io.toolisticon.jigsawbyap.api.JigsawModule;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;


/**
 * Tests of {@link JigsawModule}.
 */
public class JigsawByApProcessorTest {


    CompileTestBuilder.CompilationTestBuilder compileTestBuilder;


    @Before
    public void init() {
        MessagerUtils.setPrintMessageCodes(true);

        compileTestBuilder = CompileTestBuilder
                .compilationTest()
                .addProcessors(JigsawByApProcessor.class);
    }


    @Test
    @Ignore
    public void test_valid_usage() {

        compileTestBuilder
                .addSources(JavaFileObjectUtils.readFromResource("testcases/validUsage/package-info.java"))
                .useModules("jigsawbyap.api")
                .compilationShouldSucceed()
                .testCompilation();
    }


}