package io.toolisticon.jigsawbyap.processor.modulecompilation;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringBufferInputStream;
import java.net.URI;

public class JavaSourceFromStringFileObject extends SimpleJavaFileObject {

    private final String location;
    private final String content;


    public JavaSourceFromStringFileObject(String location, String content) {

        super(URI.create("resource:///" + location), Kind.SOURCE);
        this.location = location;
        this.content = content;

    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new StringBufferInputStream(this.content);
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(openInputStream());
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }

}