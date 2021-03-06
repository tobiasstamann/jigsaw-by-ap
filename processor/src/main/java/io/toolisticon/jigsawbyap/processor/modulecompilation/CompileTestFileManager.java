package io.toolisticon.jigsawbyap.processor.modulecompilation;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Forwarding file manager to be able to test for generated sources and resources
 */
public class CompileTestFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {


    public static class FileObjectCache<T extends FileObject> {

        final Map<URI, T> fileObjectCache = new HashMap<URI, T>();


        public boolean contains(URI uri) {
            return fileObjectCache.containsKey(uri);
        }

        public T getFileObject(URI uri) {
            return fileObjectCache.get(uri);
        }

        public void addFileObject(URI uri, T fileObject) {
            fileObjectCache.put(uri, fileObject);
        }

        public Collection<T> getEntries() {
            return fileObjectCache.values();
        }

        public boolean isEmpty() {
            return fileObjectCache.isEmpty();
        }


    }


    private final FileObjectCache<JavaFileObject> generatedJavaFileObjectCache = new FileObjectCache<JavaFileObject>();
    private final FileObjectCache<FileObject> generatedFileObjectCache = new FileObjectCache<FileObject>();


    public CompileTestFileManager(StandardJavaFileManager standardJavaFileManager) {
        super(standardJavaFileManager);


        try {


            List<File> jarFiles = getJarsFromClasspathDuringJavacCompilation();

            if (jarFiles == null) {
                jarFiles = getJarsFromClasspathDuringInlineCompilation();
            }

            // Compile test configuration
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CLASS PATH : \n");

            int i = 0;
            for (File file : jarFiles) {
                stringBuilder.append("[")
                        .append(i++)
                        .append("] := '")
                        .append(file.getAbsolutePath())
                        .append("'\n");
            }
            System.out.println(stringBuilder.toString());


            // will throw IllegalArgumentException for < Java 9
            standardJavaFileManager.setLocation(StandardLocation.MODULE_PATH, jarFiles);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    List<JavaFileObject> getGeneratedJavaFileObjects() {
        return new ArrayList<JavaFileObject>(generatedJavaFileObjectCache.getEntries());
    }

    List<FileObject> getGeneratedFileObjects() {
        return new ArrayList<FileObject>(generatedFileObjectCache.getEntries());
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        return a.toUri().equals(b.toUri());
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {

        JavaFileObject result = new InMemoryOutputJavaFileObject(uriForJavaFileObject(location, className, kind), kind);
        generatedJavaFileObjectCache.addFileObject(result.toUri(), result);
        return result;

    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
        JavaFileObject result = new InMemoryOutputJavaFileObject(uriForFileObject(location, packageName, relativeName), JavaFileObject.Kind.OTHER);
        generatedFileObjectCache.addFileObject(result.toUri(), result);
        return result;
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, JavaFileObject.Kind kind) throws IOException {

        if (location.isOutputLocation()) {

            URI uri = uriForJavaFileObject(location, className, kind);

            if (generatedJavaFileObjectCache.contains(uri)) {
                return generatedJavaFileObjectCache.getFileObject(uri);
            } else {
                throw new FileNotFoundException("Can't find JavaFileObject for uri:" + uri.toString());
            }
        }
        JavaFileObject javaFileObject = super.getJavaFileForInput(location, className, kind);
        if (javaFileObject == null) {
            throw new FileNotFoundException("Can't find JavaFileObject for : " + location.toString() + "|" + className + "|" + kind);
        }
        return javaFileObject;
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName) throws IOException {


        if (location.isOutputLocation()) {

            URI uri = uriForFileObject(location, packageName, relativeName);

            if (generatedFileObjectCache.contains(uri)) {
                return generatedFileObjectCache.getFileObject(uri);
            } else {
                throw new FileNotFoundException("Can't find FileObject for uri:" + uri.toString());
            }
        }
        return super.getFileForInput(location, packageName, relativeName);
    }


    /**
     * Checks if JavaFileObject for passed parameters exists.
     *
     * @param location
     * @param className
     * @param kind
     * @return
     */
    public boolean existsExpectedJavaFileObject(Location location, String className, JavaFileObject.Kind kind) {

        return this.generatedJavaFileObjectCache.contains(uriForJavaFileObject(location, className, kind));

    }

    /**
     * Checks if JavaFileObject for passed parameters exists.
     *
     * @param location
     * @param packageName
     * @param relativeName
     * @return
     */
    public boolean existsExpectedFileObject(Location location, String packageName, String relativeName) {

        return this.generatedFileObjectCache.contains(uriForFileObject(location, packageName, relativeName));

    }

    public static boolean contentEquals(InputStream input1, InputStream input2) throws IOException {
        if (!(input1 instanceof BufferedInputStream)) {
            input1 = new BufferedInputStream(input1);
        }
        if (!(input2 instanceof BufferedInputStream)) {
            input2 = new BufferedInputStream(input2);
        }

        int ch = input1.read();
        while (-1 != ch) {
            int ch2 = input2.read();
            if (ch != ch2) {
                return false;
            }
            ch = input1.read();
        }

        int ch2 = input2.read();
        return (ch2 == -1);
    }


    interface OutputStreamCallback {

        void setContent(byte[] content);

    }


    private static URI uriForFileObject(Location location, String packageName, String relativeName) {
        StringBuilder uri = new StringBuilder("mem:///").append(location.getName()).append('/');
        if (!packageName.isEmpty()) {
            uri.append(packageName.replace('.', '/')).append('/');
        }
        uri.append(relativeName);
        return URI.create(uri.toString());
    }

    private static URI uriForJavaFileObject(Location location, String className, JavaFileObject.Kind kind) {
        return URI.create(
                "mem:///" + location.getName() + '/' + className.replace('.', '/') + kind.extension);
    }


    public static class InMemoryOutputJavaFileObject extends SimpleJavaFileObject implements OutputStreamCallback {

        private byte[] content = new byte[0];

        public InMemoryOutputJavaFileObject(URI uri, Kind kind) {
            super(uri, kind);
        }

        @Override
        public void setContent(byte[] content) {
            this.content = content != null ? content : new byte[0];
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public OutputStream openOutputStream() throws IOException {
            return new InMemoryOutputStream(this);
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new InputStreamReader(openInputStream());
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return new String(content);
        }

        @Override
        public Writer openWriter() throws IOException {
            return new OutputStreamWriter(openOutputStream());
        }
    }

    /**
     * Does call a callback function on close to set content.
     * Have to check if it is sufficient to pass content on close.
     */
    public static class InMemoryOutputStream extends ByteArrayOutputStream {

        private final OutputStreamCallback outputStreamCallback;

        public InMemoryOutputStream(OutputStreamCallback outputStreamCallback) {
            this.outputStreamCallback = outputStreamCallback;
        }

        @Override
        public void close() throws IOException {

            super.close();
            outputStreamCallback.setContent(this.toByteArray());

        }

        @Override
        public void write(byte[] b) throws IOException {
            super.write(b);
            outputStreamCallback.setContent(this.toByteArray());
        }
    }

    /**
     * This one allows reading the classpath from compilation in applied annotation processor executed via javac.
     * @return the jar files in classpath or null if ClassLoader can't be casted to URLCLassLoader
     */
    protected static List<File> getJarsFromClasspathDuringJavacCompilation() {

        try {
            List<File> files = new ArrayList<File>();

            for (URL url : ((URLClassLoader) CompileTestFileManager.class.getClassLoader()).getURLs()) {

                if (url.getFile().endsWith(".jar")) {
                    try {
                        files.add(new File(url.toURI()));
                    } catch (Exception e) {
                        // ignore
                    }
                }

            }

            return files;
        } catch (ClassCastException e) {
            // Classloader can't be casted to URLClassloader
            // This means the method is called in context of a unit test
            return null;
        }

    }


    protected static List<File> getJarsFromClasspathDuringInlineCompilation() {
        List<File> files = new ArrayList<File>();

        files.addAll(getJarsFromProperty("java.class.path"));
        //files.addAll(getJarsFromProperty("java.library.path"));
        //files.addAll(getJarsFromProperty("jdk.module.path"));

        return files;
    }

    protected static List<File> getJarsFromProperty(String propertyName) {
        List<File> urls = new ArrayList<File>();


        if (System.getProperty(propertyName) != null) {
            String[] pathElements = System.getProperty(propertyName).split(System.getProperty("path.separator"));

            for (String pathElement : pathElements) {

                System.out.println("JAR FILE CANDIDATE [" + propertyName + "]: " + pathElement);

                if (pathElement.endsWith(".jar")) {

                    File file = new File(pathElement);
                    if (file.exists()) {
                        urls.add(file);
                    }

                }
            }
        }
        return urls;
    }
}

