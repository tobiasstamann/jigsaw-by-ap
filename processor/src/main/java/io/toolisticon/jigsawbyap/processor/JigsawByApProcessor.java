package io.toolisticon.jigsawbyap.processor;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import io.toolisticon.annotationprocessortoolkit.AbstractAnnotationProcessor;
import io.toolisticon.annotationprocessortoolkit.templating.TemplateProcessor;
import io.toolisticon.annotationprocessortoolkit.tools.ElementUtils;
import io.toolisticon.annotationprocessortoolkit.tools.MessagerUtils;
import io.toolisticon.jigsawbyap.api.JigsawExport;
import io.toolisticon.jigsawbyap.api.JigsawModule;
import io.toolisticon.jigsawbyap.processor.modulecompilation.CompileTestFileManager;
import io.toolisticon.jigsawbyap.processor.modulecompilation.JavaSourceFromStringFileObject;
import io.toolisticon.spiap.api.Service;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Annotation Processor for {@link JigsawModule}.
 */
@Service(Processor.class)
public class JigsawByApProcessor extends AbstractAnnotationProcessor {

    private final static Set<String> SUPPORTED_ANNOTATIONS = createSupportedAnnotationSet(JigsawModule.class, JigsawExport.class);

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }

    private JigsawModule jigsawModuleAnnotation;
    private PackageElement packageElement;
    private List<String> exportedPackages = new ArrayList<>();
    private boolean alreadyContainsModuleInfoFile = false;
    private List<JavaFileObject> javaFileObjects = new ArrayList<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        JavacTask.instance(processingEnv).addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {

                if (e.getKind() == TaskEvent.Kind.ENTER) {
                    System.out.println("SOURCE := " + e.getKind().name() + " ; " + e.getSourceFile().getName());
                    if (e.getTypeElement() != null) {
                        System.out.println("CLASS : " + e.getTypeElement().getQualifiedName().toString());
                    }
                    javaFileObjects.add(e.getSourceFile());

                    if (e.getSourceFile().getName().endsWith("module-info.java")) {
                        alreadyContainsModuleInfoFile = true;
                    }

                }


            }
        });
    }

    @Override
    public boolean processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (!roundEnv.processingOver()) {

            for (Element element : roundEnv.getElementsAnnotatedWith(JigsawExport.class)) {
                packageElement = (PackageElement) element;

                System.out.println("EXPORTED PACKAGE : " + packageElement);

                exportedPackages.add(packageElement.getQualifiedName().toString());

            }


            for (Element element : roundEnv.getElementsAnnotatedWith(JigsawModule.class)) {
                packageElement = (PackageElement) element;

                System.out.println("ANNOTATED PACKAGE : " + packageElement);

                // get annotation
                jigsawModuleAnnotation = element.getAnnotation(JigsawModule.class);

                if (jigsawModuleAnnotation.name().isEmpty()) {
                    MessagerUtils.error(element, JigsawByApProcessorMessages.ERROR_MODULE_NAME_IS_EMPTY);
                }

            }

            return false;
        }


        if (jigsawModuleAnnotation == null || packageElement == null) {
            return false;
        }


        // ---------------------------------------------------------------
        // -- check if element is placed in root package-info
        // ---------------------------------------------------------------
        //if (!packageElement.getQualifiedName().toString().isEmpty()) {
        // throw error
        //    MessagerUtils.error(packageElement, JigsawByApProcessorMessages.ERROR_MODULE_ANNOTATIN_MUST_BE_PLACEDIN_ROOT_PACKAGE_INFO);
        //}

        // ---------------------------------------------------------------
        // -- get module  (use APTK tools => first parent of kind MODULE)
        // ---------------------------------------------------------------
        ModuleElement moduleElement = (ModuleElement) packageElement.getEnclosingElement();

        // ---------------------------------------------------------------
        // -- check if module is unnamed (default) module => abort
        // ---------------------------------------------------------------

        System.out.println("ALREADY FOUND MODULE-INFO : " + alreadyContainsModuleInfoFile);

        if (alreadyContainsModuleInfoFile) {
            MessagerUtils.error(packageElement, JigsawByApProcessorMessages.ERROR_MODULE_INFO_ALREADY_EXISTS);
            System.out.println("ABORT!!!!!");
            return false;
        }

        System.out.println("WTF!!!!!");

        // ---------------------------------------------------------------
        // -- get all existing packages in module (enclosing elements of kind PACKAGE)
        // ---------------------------------------------------------------

        System.out.println("ENCLOSEDELEMENTS : " + moduleElement.getEnclosedElements());

        List<PackageElement> packageElements = ElementUtils.CastElement.castElementList(
                ElementUtils.AccessEnclosedElements.getEnclosedElementsOfKind(moduleElement, ElementKind.PACKAGE
                )
                , PackageElement.class);

        System.out.println("PACKAGE ELEMENTS: " + packageElements);


        // ---------------------------------------------------------------
        // -- get package-info.java files and all classes and store it
        // -- Source code can be grabbed via Trees class
        // -- it might be that package-info source files have to be created via template mechanism (APTK)
        // ---------------------------------------------------------------

        /*-
        List<JavaFileObject> packageInfoSources = new ArrayList<>();

        for (PackageElement currentPackageElement : packageElements) {
            TreePath treePath = Trees.instance(processingEnv).getPath(currentPackageElement);
            if (treePath != null) {
                packageInfoSources.add(treePath.getCompilationUnit().getSourceFile());
            }
        }

        // ---------------------------------------------------------------
        // -- get all source file for types
        // ---------------------------------------------------------------
        List<JavaFileObject> classSources = new ArrayList<>();

        for (PackageElement currentPackageElement : packageElements) {

            // get types
            List<TypeElement> types = ElementUtils.CastElement.castElementList(ElementUtils.AccessEnclosedElements.getEnclosedElementsOfKind(currentPackageElement, ElementKind.CLASS), TypeElement.class);

            for (TypeElement currentTypeElement : types) {


                TreePath treePath = Trees.instance(processingEnv).getPath(currentTypeElement);
                if (treePath != null) {

                    JavaFileObject javaFileObject = treePath.getCompilationUnit().getSourceFile();
                    classSources.add(javaFileObject);

                } else {

                    System.out.println("Couldn't get source for : '" + currentTypeElement.getQualifiedName().toString() + "' - must be compiled type");
                }

            }

        }

        System.out.println("NUMBER OF CLASS SOURCE FILE : " + classSources.size());
*/

        // ---------------------------------------------------------------
        // -- get all processors
        // ---------------------------------------------------------------

        ServiceLoader<Processor> availableProcessors = ServiceLoader.load(Processor.class, JigsawByApProcessor.class.getClassLoader());
        System.out.println("USE PROCESSORS");
        int i = 0;
        List<Processor> processors = new ArrayList<>();
        for (Processor processor : availableProcessors) {

            if (!(processor instanceof JigsawByApProcessor)) {
                System.out.println("[" + (i++) + "] := " + processor.getClass().getCanonicalName());
                processors.add(processor);
            }
        }

        // ---------------------------------------------------------------
        // -- Prepare the module-info.java source file unfortunately this cannot be generated in a regular way.
        // -- It won't be picked up and compiled correctly after source file is created
        // -- so we have to compile it on our own
        // -- need a forwarding file manager to be able to access the compiled class from memory
        // -- module-info.java files aren't not just compiled, there are some checks if exported packages exist and at least contain one class
        // -- need to add all source codes of all classes and package-info files for compilation
        // -- if we compile everything we will get feedback of module violations in all classes too ==> forward compile errors
        // ---------------------------------------------------------------


        Map<String, Object> model = new HashMap<>();
        model.put("moduleName", jigsawModuleAnnotation.name());
        model.put("requiresStatic", jigsawModuleAnnotation.requiresStatic());
        model.put("requires", jigsawModuleAnnotation.requires());
        model.put("exports", exportedPackages);

        Set<String> moduleNames = new HashSet<>();
        moduleNames.addAll(Arrays.asList(jigsawModuleAnnotation.requiresStatic()));
        moduleNames.addAll(Arrays.asList(jigsawModuleAnnotation.requires()));

        JavaFileObject javaFileObject = new JavaSourceFromStringFileObject("module-info.java", TemplateProcessor.processTemplateResourceFile("/module-info.tpl", model));


        javaFileObjects.add(javaFileObject);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

        StandardJavaFileManager stdJavaFileManager = compiler.getStandardFileManager(diagnostics, null, null);

        // Set java 9 module path if modules have been set - do it via reflection to be compatible with older java versions
        CompileTestFileManager javaFileManager = new CompileTestFileManager(compiler.getStandardFileManager(diagnostics, null, null));

        JavaCompiler.CompilationTask compilationTask = compiler.getTask(null, javaFileManager, diagnostics, null, null, javaFileObjects);

        compilationTask.setProcessors(processors);
        compilationTask.addModules(moduleNames);

        Boolean compilationSucceeded = compilationTask.call();
        if (!compilationSucceeded) {
            System.out.println("COMPILATION ERROR OCCURRED");
            MessagerUtils.error(packageElement, "WTF : " + getDiagnosticByKind(diagnostics, Diagnostic.Kind.ERROR).toString());
            return false;
        }

        // ---------------------------------------------------------------
        // -- funny thing : the compiled class then can copied to SOURCE_OUTPUT
        // ---------------------------------------------------------------

        try {

            FileObject moduleInfoClass = javaFileManager.getJavaFileForInput(StandardLocation.CLASS_OUTPUT, "module-info", JavaFileObject.Kind.CLASS);
            FileObject moduleInfoClassFileObject = getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "module-info.class");
            copyClassFile(moduleInfoClass.openInputStream(), moduleInfoClassFileObject);


        } catch (IOException e) {
            e.printStackTrace();
        }

        // check if all java files that can be processed by an annotation processor can be loaded. This includes all Classes and package-info files


        // get all packages

        // get root package
        //System.out.println("FILE:\n" + usingBufferedReader(Trees.instance(processingEnv).getPath(packageElement).getCompilationUnit().getSourceFile()));

            /*-

            PackageElement root = packageElement;

            String moduleName = packageElement.getEnclosingElement() != null && !packageElement.getEnclosingElement().getSimpleName().toString().isEmpty() ? packageElement.getEnclosingElement().getSimpleName() + "/" : "";
            try {
                String moduleAndPkg = moduleName + root.getQualifiedName().toString();
                FileObject fileObject = getFiler().getResource(StandardLocation.SOURCE_PATH, moduleAndPkg, "package-info.java");
                System.out.println("SOURCE_PATH (" + moduleAndPkg + ".package-info.java) := " + fileObject != null ? fileObject.toUri().toString() : "NULL");

                File file = new File(fileObject.toUri());
                System.out.println("FILE:\n" + usingBufferedReader(file));


            } catch (IOException e) {
                e.printStackTrace();
            }
*/


/*-
            System.out.println("WRITE MODULE-INFO.CLASS !!!!!!!!!!!!!");
            // Now copy file
            String filePath = "module-info.class";
            try {
                InputStream source = new FileInputStream(
                        "/Users/tobiasstamann/Projects/Opensource/compile-testing/compile-testing/target/compileTesting_failingUnitTests/asmtotnjft/CLASS_OUTPUT/module-info.class");
                FileObject target = ProcessingEnvironmentUtils.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filePath);

                copyClassFile(source, target);


            } catch (IOException e) {
                System.out.println("!!!! ERROR !!!!");
                e.printStackTrace();
                MessagerUtils.error(packageElement, JigsawByApProcessorMessages.ERROR_COULD_NOT_FIND_MODULE_FILE.getMessage(), filePath);
            }

            System.out.println("DONE : WRITE MODULE-INFO.CLASS !!!!!!!!!!!!!");

*/
            /*-

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();


            try {
                JavaCompiler.CompilationTask compilationTask = compiler.getTask(null, compiler.getStandardFileManager(diagnostics, null, null), diagnostics, null, null, new SimpleJavaFileObject(new URI("str://module-info.java"), JavaFileObject.Kind.SOURCE) {

                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            */

/*-
            // Now copy file
            String filePath = "module-info.java";
            try {
                FileObject target = ProcessingEnvironmentUtils.getFiler().createSourceFile(filePath);
//SimpleResourceWriter javaWriter = FilerUtils.createResource(filePath);

                InputStream source = this.getClass().getResourceAsStream(jigsawModuleAnnotation.value());


                copyClassFile(source, target);

            } catch (IOException e) {
                e.printStackTrace();
                MessagerUtils.error(packageElement, JigsawByApProcessorMessages.ERROR_COULD_NOT_FIND_MODULE_FILE.getMessage(), filePath);
            }
            */


        return false;

    }

    private void copyClassFile(InputStream source, FileObject target1) {

        OutputStream fos = null;
        InputStream is = source;

        try {


            // open input and output stream for file copy
            fos = target1.openOutputStream();

            byte[] buffer = new byte[20000];

            int ch = is.read(buffer);
            while (-1 != ch) {

                fos.write(buffer, 0, ch);
                ch = is.read(buffer);

            }

            fos.flush();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {

                }
            }

            if (fos != null) {
                try {
                    is.close();
                } catch (IOException e) {

                }
            }
        }


    }


    private static String usingBufferedReader(File file) {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {

            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                contentBuilder.append(sCurrentLine).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentBuilder.toString();
    }

    private static String usingBufferedReader(JavaFileObject file) {
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(file.openReader(true))) {

            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                contentBuilder.append(sCurrentLine).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentBuilder.toString();
    }

    /**
     * Filters Diagnostics by kind.
     *
     * @param diagnostics the compilations diagnostics result
     * @param kind        the kind of the messages to return
     * @return a Set containing all Diagnostic element of passed kind, or an empty Set.
     */
    static Set<Diagnostic> getDiagnosticByKind(DiagnosticCollector<JavaFileObject> diagnostics, Diagnostic.Kind kind) {

        Set<Diagnostic> filteredDiagnostics = new HashSet<Diagnostic>();

        for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
            if (kind == diagnostic.getKind()) {
                filteredDiagnostics.add(diagnostic);
            }
        }

        return filteredDiagnostics;

    }


}
